#!/usr/bin/env python3
import argparse
import subprocess
import sys
import fnmatch
import os

# --- Configuration ---

CRITICAL_AREAS = [
    "app/src/main/java/com/miaclean/app/data/entitlement/**",
    "functions/**",
    "firebase.json",
    ".firebaserc",
    "firestore.rules",
    "firestore.indexes.json",
    "*.gradle*",
    "gradle/**",
]

SENSITIVE_DELETION_PATTERNS = [
    "app/src/main/**",
] + CRITICAL_AREAS

SCOPE_RULES = {
    "somente-documentacao": [
        "docs/**",
        "*.md",
        "AGENTS.md",
        "PROMPTS.md",
        "RULES.md",
    ],
    "somente-testes": [
        "app/src/test/**",
        "app/src/androidTest/**",
    ],
    "refatoracao": ["*"],
    "feature": ["*"],
    "bugfix": ["*"],
    "infra": ["*"],
}

VALID_SCOPES = list(SCOPE_RULES.keys())

# --- Helper Functions ---

def match_path(path, pattern):
    if pattern == "*":
        return True
    if pattern.endswith("/**"):
        prefix = pattern[:-3]
        return path == prefix or path.startswith(prefix + "/")
    return fnmatch.fnmatch(path, pattern)

def is_match_any(path, patterns):
    return any(match_path(path, p) for p in patterns)

def get_git_changes(base=None, head=None):
    try:
        if base and head:
            cmd = ["git", "diff", "--name-status", base, head]
        else:
            cmd = ["git", "diff", "--name-status", "HEAD"]

        result = subprocess.run(cmd, capture_output=True, text=True, check=True)
        changes = []
        for line in result.stdout.strip().split("\n"):
            if not line:
                continue
            parts = line.split("\t")
            status = parts[0][0]  # Take first char: A, M, D, R, C
            paths = parts[1:]
            changes.append({"status": status, "paths": paths})
        return changes
    except subprocess.CalledProcessError as e:
        print(f"Erro ao executar git diff: {e.stderr}", file=sys.stderr)
        return []

# --- Validation Logic ---

def validate_changes(scope, changes, allowed_files, allow_critical):
    report = []
    blocked = False

    allowed_patterns = SCOPE_RULES.get(scope, [])

    for change in changes:
        status = change["status"]
        paths = change["paths"]

        for i, path in enumerate(paths):
            file_ok = True
            reason = ""

            # For R (Rename), the first path is the old path (source), which is being deleted.
            is_deletion = (status == "D") or (status == "R" and i == 0)

            is_critical = is_match_any(path, CRITICAL_AREAS)
            is_sensitive_del = (is_deletion and is_match_any(path, SENSITIVE_DELETION_PATTERNS))
            is_protected = is_critical or is_sensitive_del

            # 1. Check if it's in --allowed
            if path in allowed_files:
                if is_protected and not allow_critical:
                    file_ok = False
                    reason = "Arquivo protegido (crítico ou deleção sensível) não pode ser liberado apenas com --allowed"
                else:
                    file_ok = True
                    reason = "Liberado via --allowed"

            # 2. Check if it's protected (critical or sensitive deletion)
            elif is_protected:
                if allow_critical:
                    file_ok = True
                    reason = "Liberado via --allow-critical"
                elif scope == "infra":
                    file_ok = True
                    reason = "Permitido em escopo infra"
                else:
                    file_ok = False
                    if is_critical:
                        reason = "Alteração em área crítica fora de infra"
                    else:
                        reason = "Deleção sensível fora de infra"

            # 3. Check scope rules
            elif not is_match_any(path, allowed_patterns):
                file_ok = False
                reason = f"Arquivo fora do escopo '{scope}'"

            report.append({
                "path": path,
                "status": status,
                "ok": file_ok,
                "reason": reason,
                "critical": is_protected
            })

            if not file_ok:
                blocked = True

    return report, blocked

# --- Main ---

def main():
    parser = argparse.ArgumentParser(description="Validador de escopo de tarefas.")
    parser.add_argument("--type", "--scope", dest="scope", help="Tipo/Escopo da tarefa")
    parser.add_argument("--base", help="SHA base para comparação")
    parser.add_argument("--head", help="SHA head para comparação")
    parser.add_argument("--allowed", action="append", default=[], help="Arquivos permitidos explicitamente")
    parser.add_argument("--allow-critical", action="store_true", help="Forçar liberação de áreas críticas")

    args = parser.parse_args()

    if not args.scope:
        print("Erro: O argumento --scope ou --type é obrigatório.", file=sys.stderr)
        sys.exit(2)

    if args.scope not in VALID_SCOPES:
        print(f"Erro: Escopo inválido '{args.scope}'. Escopos válidos: {', '.join(VALID_SCOPES)}", file=sys.stderr)
        sys.exit(2)

    changes = get_git_changes(args.base, args.head)

    if not changes:
        print("Nenhuma alteração detectada.")
        print("\nResultado: PASSOU")
        sys.exit(0)

    report, is_blocked = validate_changes(args.scope, changes, args.allowed, args.allow_critical)

    # Output Report
    print(f"Relatório de Validação de Escopo")
    print(f"================================")
    print(f"Escopo: {args.scope}")
    if args.base and args.head:
        print(f"Comparando: {args.base}...{args.head}")
    else:
        print(f"Comparando: HEAD (mudanças não commitadas ou último commit)")
    print(f"--------------------------------")

    print(f"Verificando {len(report)} caminhos alterados...")

    critical_risks = []
    blocked_files = []

    for item in report:
        status_str = f"({item['status']})"
        if item["ok"]:
            print(f"[OK] {item['path']} {status_str}")
        else:
            print(f"[ERRO] {item['path']} {status_str}")
            print(f"      Causa: {item['reason']}")
            blocked_files.append(item['path'])
            if item["critical"]:
                critical_risks.append(item['path'])

    print(f"--------------------------------")
    if blocked_files:
        print(f"Arquivos bloqueados: {len(blocked_files)}")
    if critical_risks:
        print(f"Riscos críticos: {len(critical_risks)}")

    if is_blocked:
        print(f"\nResultado: BLOQUEADO")
        sys.exit(1)
    else:
        print(f"\nResultado: PASSOU")
        sys.exit(0)

if __name__ == "__main__":
    main()
