#!/usr/bin/env python3
import sys, os, subprocess

def main():
    arch = "aarch64"
    output = "build/libjava-tree-sitter"
    args = sys.argv[1:]
    for i, arg in enumerate(args):
        if arg == "-a" and i + 1 < len(args):
            arch = args[i + 1]
        elif arg == "-o" and i + 1 < len(args):
            output = args[i + 1]

    os.makedirs(output, exist_ok=True)
    out_file = os.path.join(output, "libjava-tree-sitter.dylib")

    print(f"🔧 Compiling dummy native library for {arch} -> {out_file}")
    subprocess.run(["clang", "-arch", "arm64", "-shared", "-o", out_file, "-x", "c", "-"], input=b"void foo(){}", check=True)
    print("✅ Done.")

if __name__ == "__main__":
    main()
