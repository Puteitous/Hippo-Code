//! Tree-sitter WASM 解析器
//!
//! 编译为目标: wasm32-wasip1
//! 通过 Chicory (WasiPreview1) 在 Java 中加载，零 stubs 依赖。
//!
//! # 协议
//!
//! ## alloc(size: i32) -> i32
//! 分配指定大小的内存，返回线性内存中的指针地址。
//!
//! ## dealloc(ptr: i32, size: i32)
//! 释放由 alloc 分配的内存。
//!
//! ## parse(code_ptr: i32, code_len: i32, lang_ptr: i32, lang_len: i32) -> i64
//! 解析代码并返回 JSON 结果。
//! 返回值：高 32 位 = 结果字符串的指针，低 32 位 = 长度。
//!
//! ### 成功响应
//! ```json
//! {
//!   "valid": false,
//!   "errors": [
//!     {"row": 0, "column": 8, "kind": "ERROR", "message": "expected '}'"},
//!     {"row": 2, "column": 0, "kind": "MISSING", "message": "expected end of file"}
//!   ]
//! }
//! ```
//!
//! ### 错误响应
//! ```json
//! { "error": "Unsupported language: foobar" }
//! ```

use serde::Serialize;
use std::ptr;
use tree_sitter::Parser;

// ====================================================================
// 内存管理 —— 使用 WASI SDK 的 malloc/free（与 Java 端共用同一分配器）
// ====================================================================

extern "C" {
    fn malloc(size: usize) -> *mut u8;
    fn free(ptr: *mut u8);
}

/// 分配内存（Java 端写入输入数据前调用）
#[no_mangle]
pub extern "C" fn alloc(size: i32) -> *mut u8 {
    unsafe { malloc(size as usize) }
}

/// 释放内存（Java 端处理完结果后调用）
#[no_mangle]
pub extern "C" fn dealloc(ptr: *mut u8, _size: i32) {
    if !ptr.is_null() {
        unsafe { free(ptr) }
    }
}

// ====================================================================
// 解析入口
// ====================================================================

/// 语法诊断结果中的单个错误
#[derive(Serialize)]
struct SyntaxError {
    row: u32,
    column: u32,
    kind: String,    // "ERROR" 或 "MISSING"
    message: String,
}

/// 解析结果 JSON 结构
#[derive(Serialize)]
struct ParseOutput {
    #[serde(skip_serializing_if = "Option::is_none")]
    valid: Option<bool>,
    #[serde(skip_serializing_if = "Option::is_none")]
    errors: Option<Vec<SyntaxError>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    error: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    language: Option<String>,
}

/// 主解析函数
///
/// # 参数
/// - `code_ptr`, `code_len`: 源码字符串在 WASM 线性内存中的位置
/// - `lang_ptr`, `lang_len`: 语言名称在 WASM 线性内存中的位置（如 "java"）
///
/// # 返回值
/// 打包的 i64: 高 32 位 = 结果 JSON 字符串的指针，低 32 位 = 长度
#[no_mangle]
pub extern "C" fn parse(
    code_ptr: i32,
    code_len: i32,
    lang_ptr: i32,
    lang_len: i32,
) -> i64 {
    let code = read_string(code_ptr, code_len);
    let lang = read_string(lang_ptr, lang_len);

    let output = match parse_inner(&code, &lang) {
        Ok(ParseSuccess { valid, errors, language }) => ParseOutput {
            valid: Some(valid),
            errors: Some(errors),
            error: None,
            language: Some(language),
        },
        Err(msg) => ParseOutput {
            valid: None,
            errors: None,
            error: Some(msg),
            language: None,
        },
    };

    let json = serde_json::to_string(&output).unwrap_or_else(|e| {
        format!("{{\"error\":\"JSON serialization failed: {}\"}}", e)
    });

    leak_string(json)
}

// ====================================================================
// 内部解析逻辑
// ====================================================================

struct ParseSuccess {
    valid: bool,
    errors: Vec<SyntaxError>,
    language: String,
}

fn parse_inner(code: &str, lang: &str) -> Result<ParseSuccess, String> {
    let language = resolve_language(lang).ok_or_else(|| format!("Unsupported language: {}", lang))?;

    let mut parser = Parser::new();
    parser
        .set_language(&language)
        .map_err(|e| format!("Failed to set language '{}': {}", lang, e))?;

    let tree = parser
        .parse(code, None)
        .ok_or_else(|| "Parser returned no tree".to_string())?;

    let root = tree.root_node();

    // 不能只用 root.has_error() 做短路判断——has_error() 只检查 ERROR 节点，
    // 缺分号、缺括号等场景产生的是 MISSING 节点，不会被 has_error() 覆盖。
    // 必须完整遍历整棵树收集 ERROR + MISSING。
    let mut errors = Vec::new();
    collect_errors(&root, code, &mut errors);

    if errors.is_empty() {
        return Ok(ParseSuccess {
            valid: true,
            errors: vec![],
            language: lang.to_string(),
        });
    }

    Ok(ParseSuccess {
        valid: false,
        errors,
        language: lang.to_string(),
    })
}

/// 递归收集语法树中的 ERROR 和 MISSING 节点
fn collect_errors(node: &tree_sitter::Node, source: &str, errors: &mut Vec<SyntaxError>) {
    if node.is_error() || node.is_missing() {
        let pos = node.start_position();
        let row = pos.row as u32;
        let column = pos.column as u32;
        let kind = if node.is_missing() {
            "MISSING".to_string()
        } else {
            "ERROR".to_string()
        };
        let message = if node.is_missing() {
            format!("缺少语法元素: {}", node.kind())
        } else {
            let context = extract_context(source, row, column);
            format!("语法错误 (发现: {}){}", node.kind(),
                if !context.is_empty() { format!(" 附近: {}", context) } else { String::new() })
        };

        errors.push(SyntaxError {
            row,
            column,
            kind,
            message,
        });
    }

    // 递归遍历所有子节点——MISSING 节点可能是匿名子节点（如缺少的 `;`），
    // 不能用 is_named() 过滤，否则会漏掉。
    let mut cursor = node.walk();
    for child in node.children(&mut cursor) {
        collect_errors(&child, source, errors);
    }
}

/// 从源码中提取错误位置附近的上下文（最多 40 个字符）
fn extract_context(source: &str, row: u32, column: u32) -> String {
    let line = source.lines().nth(row as usize).unwrap_or("");
    let line_len = line.len();
    if line_len == 0 {
        return String::new();
    }

    let col = (column as usize).min(line_len.saturating_sub(1));
    let start = col.saturating_sub(15);
    let end = (col + 15).min(line_len);
    let mut ctx = &line[start..end];

    if start > 0 {
        ctx = ctx.trim_start();
    }
    if end < line_len {
        ctx = ctx.trim_end();
    }

    ctx.to_string()
}

// ====================================================================
// 语言注册表
// ====================================================================

fn resolve_language(name: &str) -> Option<tree_sitter::Language> {
    // 语言 crate 0.23 暴露 LANGUAGE 常量（LanguageFn），
    // tree-sitter 0.24 实现了 From<LanguageFn> for Language，直接用 .into()。
    // 注意：tree_sitter_typescript 使用 LANGUAGE_TYPESCRIPT / LANGUAGE_TSX。
    macro_rules! load_lang {
        ($lang:ident) => {
            $lang::LANGUAGE.into()
        };
    }

    match name {
        "java" => Some(load_lang!(tree_sitter_java)),
        "javascript" | "js" => Some(load_lang!(tree_sitter_javascript)),
        "typescript" | "ts" => Some(tree_sitter_typescript::LANGUAGE_TYPESCRIPT.into()),
        "tsx" => Some(tree_sitter_typescript::LANGUAGE_TSX.into()),
        "python" | "py" => Some(load_lang!(tree_sitter_python)),
        "go" | "golang" => Some(load_lang!(tree_sitter_go)),
        "rust" | "rs" => Some(load_lang!(tree_sitter_rust)),
        "html" | "htm" => Some(load_lang!(tree_sitter_html)),
        "css" | "scss" | "less" => Some(load_lang!(tree_sitter_css)),
        "json" => Some(load_lang!(tree_sitter_json)),
        _ => None,
    }
}

// ====================================================================
// 辅助函数
// ====================================================================

/// 从 WASM 线性内存读取指定位置的字符串
fn read_string(ptr: i32, len: i32) -> String {
    if ptr == 0 || len <= 0 {
        return String::new();
    }
    unsafe {
        let bytes = std::slice::from_raw_parts(ptr as *const u8, len as usize);
        String::from_utf8_lossy(bytes).to_string()
    }
}

/// 将字符串写入 WASM 线性内存并返回打包的 (ptr << 32 | len)
fn leak_string(s: String) -> i64 {
    let bytes = s.as_bytes();
    let len = bytes.len();

    unsafe {
        let ptr = malloc(len);
        if ptr.is_null() {
            // 分配失败，返回错误 JSON
            let err = "{\"error\":\"Memory allocation failed\"}";
            let err_bytes = err.as_bytes();
            let err_ptr = malloc(err_bytes.len());
            ptr::copy_nonoverlapping(err_bytes.as_ptr(), err_ptr, err_bytes.len());
            return (((err_ptr as u32 as u64) << 32) | (err_bytes.len() as u32 as u64)) as i64;
        }
        ptr::copy_nonoverlapping(bytes.as_ptr(), ptr, len);
        (((ptr as u32 as u64) << 32) | (len as u32 as u64)) as i64
    }
}
