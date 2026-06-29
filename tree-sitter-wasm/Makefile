# Tree-sitter WASM 构建脚本
#
# 前提条件:
#   1. Rust 工具链 (rustup)
#   2. wasm32-wasip1 目标: rustup target add wasm32-wasip1
#   3. (可选) WASI SDK: 提供 malloc/free 的 C 标准库
#      - 不安装 WASI SDK 也能编译，rustc 内置 wasi-libc
#   4. (可选) Wizer: 预初始化优化，大幅提升首次加载速度
#   5. (可选) Binaryen: wasm-opt 优化体积
#
# 快速开始:
#   make all          # 完整构建（含 Wizer + wasm-opt）
#   make build        # 仅编译 WASM，不做优化
#   make release      # 将优化后的 WASM 复制到 Java resources

WASI_SDK_VERSION := 29
WIZER_VERSION := 10.0.0
BINARYEN_VERSION := 125
PWD := $(CURDIR)

# 输出的 WASM 文件路径（Java resources 目录）
JAVA_RESOURCES := ../src/main/resources/tree-sitter
OUTPUT_WASM := $(JAVA_RESOURCES)/tree-sitter-parser.wasm

.PHONY: clean
clean:
	rm -rf target wasi-sdk* wizer* binaryen*

.PHONY: fmt
fmt:
	cargo fmt

# ---- 工具链下载 ----

.PHONY: get-wasisdk
get-wasisdk:
	@if [ ! -d "$(PWD)/wasi-sdk" ]; then \
		wget https://github.com/WebAssembly/wasi-sdk/releases/download/wasi-sdk-$(WASI_SDK_VERSION)/wasi-sdk-$(WASI_SDK_VERSION).0-x86_64-linux.tar.gz && \
		tar xvf wasi-sdk-$(WASI_SDK_VERSION).0-x86_64-linux.tar.gz && \
		rm wasi-sdk-$(WASI_SDK_VERSION).0-x86_64-linux.tar.gz && \
		mv wasi-sdk-$(WASI_SDK_VERSION).0-x86_64-linux $(PWD)/wasi-sdk; \
	fi

.PHONY: get-wizer
get-wizer:
	@if [ ! -d "$(PWD)/wizer" ]; then \
		wget https://github.com/bytecodealliance/wizer/releases/download/v$(WIZER_VERSION)/wizer-v$(WIZER_VERSION)-x86_64-linux.tar.xz && \
		tar xvf wizer-v$(WIZER_VERSION)-x86_64-linux.tar.xz && \
		rm wizer-v$(WIZER_VERSION)-x86_64-linux.tar.xz && \
		mv wizer-v$(WIZER_VERSION)-x86_64-linux $(PWD)/wizer; \
	fi

.PHONY: get-binaryen
get-binaryen:
	@if [ ! -d "$(PWD)/binaryen" ]; then \
		wget https://github.com/WebAssembly/binaryen/releases/download/version_$(BINARYEN_VERSION)/binaryen-version_$(BINARYEN_VERSION)-x86_64-linux.tar.gz && \
		tar xvf binaryen-version_$(BINARYEN_VERSION)-x86_64-linux.tar.gz && \
		rm binaryen-version_$(BINARYEN_VERSION)-x86_64-linux.tar.gz && \
		mv binaryen-version_$(BINARYEN_VERSION) $(PWD)/binaryen; \
	fi

# ---- 编译 ----

.PHONY: build
build:
	cargo build --target wasm32-wasip1 --release

.PHONY: optimize
optimize: build
#	Wizer 预初始化: 提前解析所有语言 grammar，消除冷启动时间
#	$(PWD)/wizer/wizer --allow-wasi target/wasm32-wasip1/release/tree-sitter-wasm.wasm \
#		-o target/wasm32-wasip1/release/tree-sitter-wasm-initialized.wasm
#	Binaryen 优化: 去除调试信息，优化代码体积
#	$(PWD)/binaryen/bin/wasm-opt -Oz --strip-debug \
#		target/wasm32-wasip1/release/tree-sitter-wasm-initialized.wasm \
#		-o target/wasm32-wasip1/release/tree-sitter-wasm-opt.wasm
#	当前（无 Wizer）直接复制 release 输出
	cp target/wasm32-wasip1/release/tree-sitter-wasm.wasm \
		target/wasm32-wasip1/release/tree-sitter-wasm-opt.wasm

.PHONY: release
release: optimize
	mkdir -p $(JAVA_RESOURCES)
	cp target/wasm32-wasip1/release/tree-sitter-wasm-opt.wasm $(OUTPUT_WASM)
	@echo "=== WASM 已部署到 $(OUTPUT_WASM) ==="
	ls -lh $(OUTPUT_WASM)

# ---- 一站式 ----

# 完整构建（含下载工具链 + Wizer + wasm-opt）
.PHONY: all
all: get-wasisdk get-wizer get-binaryen release

# 快速构建（仅编译 + 复制）
.PHONY: fast
fast: build release
