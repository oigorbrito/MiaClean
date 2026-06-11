import os
import sys
import argparse
import fnmatch
import subprocess

# Task types and their allowed patterns
TASK_TYPES = {
    "somente-documentacao": ["README.md", "docs/*", "*.md", "AGENTS.md", "PROMPTS.md", "RULES.md"],
    "somente-testes": ["app/src/test/*", "app/src/androidTest/*"],
    "refatoracao": ["app/src/main/*"],
    "feature": ["app/src/main/*", "app/src/test/*", "app/src/androidTest/*"],
    "bugfix": ["app/src/main/*", "app/src/test/*", "app/src/androidTest/*"],
    "infra": ["*.gradle*", "gradle/*", "Dockerfile", ".github/*", "firebase.json", "firestore.*", ".firebaserc", "functions/*", "harness/*", "AGENTS.md", "docs/*", "PROMPTS.md", "RULES.md"]
}

# Critical files that should almost never be changed by agents unless infra task
CRITICAL_PROTECTIONS = [
    "app/src/main/java/com/miaclean/app/data/entitlement/*",
    "functions/*",
    "firebase.json",
    ".firebaserc",
    "firestore.rules",
    "firestore.indexes.json"
]

# Sensitive files whose deletion is considered critical risk
SENSITIVE_DELETIONS = [
    "functions/*",
    "app/src/main/*",
    "app/src/main/java/com/miaclean/app/data/entitlement/*",
    "*.gradle*",
    "gradle/*",
    "firebase.json",
    ".firebaserc",
    "firestore.*"
]

def get_changed_files():
    try:
        # Get all changes (staged and unstaged) compared to HEAD
        result = subprocess.run(['git', 'diff', '--name-status', 'HEAD'], capture_output=True, text=True)
        lines = result.stdout.splitlines()

        changed_files = {}
        for line in lines:
            if not line: continue
            # git diff --name-status format: STATUS\tPATH or STATUS\tOLD_PATH\tNEW_PATH
            parts = line.split('\t')
            status = parts[0]
            if status.startswith('R') or status.startswith('C'):
                # Renamed or Copied: Status path1 path2
                path = parts[2]
            else:
                path = parts[1]
            changed_files[path] = status
        return changed_files
    except Exception as e:
        print(f"Erro ao coletar arquivos do git: {e}")
        return {}

def validate_changes(task_type, changed_files_dict, allowed_overrides=None):
    if task_type not in TASK_TYPES:
        return False, f"Tipo de tarefa inválido: {task_type}", False

    allowed_patterns = TASK_TYPES[task_type][:]
    if allowed_overrides:
        allowed_patterns.extend(allowed_overrides)

    blocked_files = []
    is_critical_risk = False

    for file_path, status in changed_files_dict.items():
        if not file_path: continue

        # Check for sensitive deletions first
        is_this_file_critical = False
        if status == 'D' and task_type != "infra":
            for sensitive_pattern in SENSITIVE_DELETIONS:
                if fnmatch.fnmatch(file_path, sensitive_pattern):
                    blocked_files.append(f"{file_path} (DELEÇÃO CRÍTICA)")
                    is_critical_risk = True
                    is_this_file_critical = True
                    break

        if is_this_file_critical:
            continue

        is_allowed = False
        for pattern in allowed_patterns:
            if fnmatch.fnmatch(file_path, pattern):
                is_allowed = True
                break

        # Explicit critical protections for modifications/additions
        if task_type != "infra":
            for crit_pattern in CRITICAL_PROTECTIONS:
                if fnmatch.fnmatch(file_path, crit_pattern):
                    # Even if matched by a broad pattern like app/src/main/*,
                    # critical files are blocked unless it's an infra task or explicitly overridden
                    is_allowed = False
                    # Check if it's in overrides
                    if allowed_overrides:
                        for override in allowed_overrides:
                            if fnmatch.fnmatch(file_path, override):
                                is_allowed = True
                                break
                    if not is_allowed:
                        break

        if not is_allowed:
            blocked_files.append(file_path)

    if blocked_files:
        return False, blocked_files, is_critical_risk
    return True, [], False

def main():
    parser = argparse.ArgumentParser(description="Valida mudanças de arquivos com base no tipo de tarefa.")
    parser.add_argument("--type", help="Tipo da tarefa (e.g., somente-documentacao, feature, etc.)")
    parser.add_argument("--files", help="Lista de arquivos alterados (opcional, se omitido usa git diff, formato: path:status,path:status)")
    parser.add_argument("--allowed", help="Lista de arquivos/padrões explicitamente permitidos (separados por vírgula)")

    args = parser.parse_args()

    task_type = args.type
    if not task_type:
        # Try to extract from TASK_TEMPLATE.md if it exists
        if os.path.exists("harness/TASK_TEMPLATE.md"):
            with open("harness/TASK_TEMPLATE.md", "r") as f:
                content = f.read()
                for line in content.splitlines():
                    if "**Type**:" in line:
                        task_type = line.split(":")[-1].strip().replace("[", "").replace("]", "").split("|")[0].strip()
                        break

    if not task_type or ("somente-documentacao" in task_type and "|" in task_type):
         print("ERRO: Tipo de tarefa não especificado ou ainda no formato de template.")
         print("Por favor, edite harness/TASK_TEMPLATE.md e defina o tipo da tarefa.")
         sys.exit(1)

    changed_files_dict = {}
    if args.files:
        pairs = [p.strip() for p in args.files.split(",") if p.strip()]
        for p in pairs:
            if ":" in p:
                path, status = p.split(":", 1)
                changed_files_dict[path] = status
            else:
                changed_files_dict[p] = 'M' # Default to Modified
    else:
        changed_files_dict = get_changed_files()

    if not changed_files_dict:
        print("Nenhuma mudança detectada.")
        sys.exit(0)

    allowed_overrides = [f.strip() for f in args.allowed.split(",")] if args.allowed else []

    success, result, is_critical = validate_changes(task_type, changed_files_dict, allowed_overrides)

    print(f"--- Relatório de Validação de Escopo ---")
    print(f"Tipo de Tarefa: {task_type}")
    print(f"Arquivos Alterados: {len(changed_files_dict)}")

    if success:
        print("Resultado: PASSOU")
        sys.exit(0)
    else:
        if is_critical:
            print("Resultado: BLOQUEADO (RISCO CRÍTICO)")
        else:
            print("Resultado: BLOQUEADO")

        print("Os seguintes arquivos estão fora do escopo permitido ou representam risco:")
        for f in result:
            print(f" [!] {f}")

        if is_critical:
            print("\nAVISO: Deleções de arquivos sensíveis foram detectadas. Isso requer uma tarefa do tipo 'infra'.")
        else:
            print("\nSe essas mudanças forem necessárias, declare-as em 'Allowed Overrides' no TASK_TEMPLATE.md ou mude o tipo da tarefa.")
        sys.exit(1)

if __name__ == "__main__":
    main()
