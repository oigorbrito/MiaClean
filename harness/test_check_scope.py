import unittest
from harness.check_scope import validate_changes

class TestCheckScope(unittest.TestCase):

    def test_documentacao_md_pass(self):
        # Somente documentação com arquivo .md modificado deve passar
        changes = {"README.md": "M"}
        success, blocked, critical = validate_changes("somente-documentacao", changes)
        self.assertTrue(success)
        self.assertEqual(len(blocked), 0)
        self.assertFalse(critical)

    def test_documentacao_kotlin_block(self):
        # Somente documentação com arquivo Kotlin modificado deve bloquear
        changes = {"app/src/main/java/MainActivity.kt": "M"}
        success, blocked, critical = validate_changes("somente-documentacao", changes)
        self.assertFalse(success)
        self.assertIn("app/src/main/java/MainActivity.kt", blocked)
        self.assertFalse(critical)

    def test_documentacao_firebase_delete_block_critical(self):
        # Somente documentação com arquivo Firebase deletado deve bloquear com risco crítico
        changes = {"firebase.json": "D"}
        success, blocked, critical = validate_changes("somente-documentacao", changes)
        self.assertFalse(success)
        self.assertTrue(any("firebase.json" in b for b in blocked))
        self.assertTrue(critical)

    def test_infra_harness_pass(self):
        # Infra alterando arquivos do harness deve passar
        changes = {"harness/check_scope.py": "M"}
        success, blocked, critical = validate_changes("infra", changes)
        self.assertTrue(success)
        self.assertEqual(len(blocked), 0)
        self.assertFalse(critical)

    def test_non_infra_delete_gradle_block_critical(self):
        # Bugfix deletando arquivo de build deve bloquear com risco crítico
        changes = {"build.gradle.kts": "D"}
        success, blocked, critical = validate_changes("bugfix", changes)
        self.assertFalse(success)
        self.assertTrue(any("build.gradle.kts" in b for b in blocked))
        self.assertTrue(critical)

    def test_feature_modify_entitlement_block(self):
        # Feature modificando entitlement deve bloquear (proteção explícita)
        changes = {"app/src/main/java/com/miaclean/app/data/entitlement/EntitlementRepository.kt": "M"}
        success, blocked, critical = validate_changes("feature", changes)
        self.assertFalse(success)
        self.assertIn("app/src/main/java/com/miaclean/app/data/entitlement/EntitlementRepository.kt", blocked)
        self.assertFalse(critical)

if __name__ == "__main__":
    unittest.main()
