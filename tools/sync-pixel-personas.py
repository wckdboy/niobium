#!/usr/bin/env python3
"""Embed the shared persona catalog for offline native generation."""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIELDS = ["id", "role", "keywords", "tools", "traits", "voice", "practice", "creature"]
START = "// BEGIN GENERATED PERSONAS — tools/data/pixel-personas.json"
END = "// END GENERATED PERSONAS"

def declarations():
    catalog = json.loads((ROOT / "tools/data/pixel-personas.json").read_text())
    quote = lambda value: json.dumps(value, ensure_ascii=False)
    swift = START + """
private struct PixelPersona {
    let id: String
    let role: String
    let keywords: [String]
    let tools: [String]
    let traits: [String]
    let voice: String
    let practice: String
    let creature: String
}
private let pixelPersonas: [PixelPersona] = [
"""
    kotlin = START + """
private data class PixelPersona(
    val id: String, val role: String, val keywords: List<String>, val tools: List<String>,
    val traits: List<String>, val voice: String, val practice: String, val creature: String
)
private val pixelPersonas = listOf(
"""
    def kotlin_value(value):
        return "listOf(" + ", ".join(map(quote, value)) + ")" if isinstance(value, list) else quote(value)
    for row in catalog:
        swift += "    .init(" + ", ".join(key + ": " + quote(row[key]) for key in FIELDS) + "),\n"
        kotlin += "    PixelPersona(" + ", ".join(kotlin_value(row[key]) for key in FIELDS) + "),\n"
    return {
        "ios/Sources/Niobium/PixelSpec.swift": swift + "]\n" + END,
        "android/app/src/main/java/io/sovware/niobium/PixelBuilder.kt": kotlin + ")\n" + END
    }

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    for name, generated in declarations().items():
        path = ROOT / name
        text = path.read_text()
        start, end = text.index(START), text.index(END) + len(END)
        if args.check:
            if text[start:end] != generated:
                raise SystemExit("Persona catalog differs: " + name)
        else:
            path.write_text(text[:start] + generated + text[end:])
    print("Verified persona catalog" if args.check else "Updated native persona catalogs")

if __name__ == "__main__":
    main()
