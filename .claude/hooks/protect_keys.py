"""Хук PreToolUse: не даёт менять и удалять ключ подписи и keystore.properties.

Без этого ключа приложение нельзя обновить поверх установленного, поэтому
правки файлов ключа и опасные команды с ними блокируются (код выхода 2).
"""
import json
import re
import sys

PROTECTED = re.compile(r"(keystore\.properties|\.jks|\.keystore)\b", re.IGNORECASE)
DANGEROUS_COMMAND = re.compile(
    r"\b(rm|mv|cp|del|erase|rd|rmdir|Remove-Item|Move-Item|Copy-Item|Set-Content|Out-File|"
    r"git\s+add|git\s+rm|curl|wget|scp|gh\s+(gist|release|secret))\b|>|\btee\b",
    re.IGNORECASE,
)


def block(reason: str) -> None:
    sys.stderr.reconfigure(encoding="utf-8")
    print(reason, file=sys.stderr)
    sys.exit(2)


def main() -> None:
    data = json.load(sys.stdin)
    tool = data.get("tool_name", "")
    args = data.get("tool_input", {}) or {}

    if tool in ("Edit", "Write", "NotebookEdit"):
        path = str(args.get("file_path", ""))
        if PROTECTED.search(path):
            block(f"Файл ключа подписи защищён: {path}. Меняйте его вручную.")
    elif tool in ("Bash", "PowerShell"):
        command = str(args.get("command", ""))
        if PROTECTED.search(command) and DANGEROUS_COMMAND.search(command):
            block("Команда трогает ключ подписи или keystore.properties (удаление, копирование, отправка). "
                  "Выполните её вручную, если уверены.")


if __name__ == "__main__":
    main()
