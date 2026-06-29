# Tree-sitter WASM Windows 构建脚本
#
# 前提条件:
#   1. Rust 工具链: https://rustup.rs
#   2. 添加 wasm32-wasip1 目标:
#      rustup target add wasm32-wasip1
#   3. (可选) WASI SDK: https://github.com/WebAssembly/wasi-sdk/releases
#      下载 wasi-sdk-29.0-x86_64-windows.zip 并解压到 .\wasi-sdk\
#      Rust 的 wasm32-wasip1 目标自带 wasi-libc，不装也能编译
#
# 使用方式:
#   powershell -ExecutionPolicy Bypass -File BUILD_WINDOWS.ps1
#   或直接在 PowerShell 中: .\BUILD_WINDOWS.ps1

$ErrorActionPreference = "Stop"

Write-Host "=== Building tree-sitter-wasm (wasm32-wasip1) ===" -ForegroundColor Cyan

# 设置 WASI_SYSROOT（cc-rs 编译 tree-sitter C 源码时需要）
$env:WASI_SYSROOT = "E:/SDK/wasi-sdk/wasi-sdk-24.0-x86_64-windows/share/wasi-sysroot"

# Step 1: 编译
Write-Host ">>> cargo build --target wasm32-wasip1 --release ..." -ForegroundColor Yellow
cargo build --target wasm32-wasip1 --release
if ($LASTEXITCODE -ne 0) {
    Write-Host "✗ Build failed" -ForegroundColor Red
    exit 1
}
Write-Host "✓ Build succeeded" -ForegroundColor Green

# Step 2: 复制到 Java resources
$targetDir = "..\src\main\resources\tree-sitter"
if (-not (Test-Path $targetDir)) {
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
}

$wasmSource = "target\wasm32-wasip1\release\tree_sitter_wasm.wasm"
$wasmDest = "$targetDir\tree-sitter-parser.wasm"

Copy-Item -Path $wasmSource -Destination $wasmDest -Force
$fileSize = (Get-Item $wasmDest).Length

Write-Host "✓ WASM deployed to $wasmDest" -ForegroundColor Green
Write-Host "  File size: $('{0:N0}' -f $fileSize) bytes ($('{0:N2}' -f ($fileSize/1KB)) KB)" -ForegroundColor Green

# Step 3: 确认
if (Test-Path $wasmDest) {
    Write-Host "`n=== Build complete ===" -ForegroundColor Cyan
} else {
    Write-Host "✗ File copy failed" -ForegroundColor Red
    exit 1
}
