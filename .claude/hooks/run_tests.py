"""Хук Stop: в конце хода запускает юнит-тесты, если код в app/src изменился.

Состояние app/src (правки относительно последнего коммита и новые файлы) сворачивается в отпечаток.
Если он совпадает с отпечатком последнего запуска, тесты не запускаются: так неизменённый код не гоняется
повторно на каждом ходу, а упавшие тесты не зацикливают работу. Упали тесты: код выхода 2, и Claude получает вывод.
"""
import hashlib
import json
import os
import subprocess
import sys
from pathlib import Path

PROJECT = Path(os.environ.get("CLAUDE_PROJECT_DIR") or Path(__file__).resolve().parents[2])
STATE = PROJECT / ".claude" / "hooks" / ".last_tested"
STUDIO_JAVA = Path(r"C:\Program Files\Android\Android Studio\jbr")
TAIL_LINES = 40


def git(*args: str) -> bytes:
    return subprocess.run(["git", *args], cwd=PROJECT, capture_output=True, check=False).stdout


def fingerprint() -> str:
    digest = hashlib.sha256()
    digest.update(git("diff", "HEAD", "--", "app/src"))
    for name in sorted(git("ls-files", "--others", "--exclude-standard", "-z", "--", "app/src").split(b"\0")):
        if not name:
            continue
        digest.update(name)
        try:
            digest.update((PROJECT / name.decode("utf-8")).read_bytes())
        except OSError:
            pass
    return digest.hexdigest()


def main() -> None:
    json.load(sys.stdin)  # содержимое не нужно, но вход нужно прочитать
    if not git("status", "--porcelain", "--", "app/src").strip():
        return
    current = fingerprint()
    if STATE.exists() and STATE.read_text(encoding="utf-8").strip() == current:
        return

    env = dict(os.environ)
    if STUDIO_JAVA.exists():
        env["JAVA_HOME"] = str(STUDIO_JAVA)
    result = subprocess.run(
        [str(PROJECT / "gradlew.bat"), "testDebugUnitTest", "--console=plain", "-q"],
        cwd=PROJECT, env=env, capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    STATE.write_text(current, encoding="utf-8")
    if result.returncode == 0:
        return

    output = (result.stdout + "\n" + result.stderr).strip().splitlines()
    sys.stderr.reconfigure(encoding="utf-8")
    print("Юнит-тесты упали. Последние строки вывода Gradle:\n" + "\n".join(output[-TAIL_LINES:]), file=sys.stderr)
    sys.exit(2)


if __name__ == "__main__":
    main()
