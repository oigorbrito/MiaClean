import json
import sys

def run_eval():
    print("Starting Clinical AI Evaluation...")
    try:
        with open('harness/ai_eval/clinical_dataset.json', 'r') as f:
            dataset = json.load(f)

        print(f"Loaded {len(dataset)} test cases.")

        # Simulação de avaliação
        for case in dataset:
            print(f"Evaluating Case {case['id']}...")
            # Aqui entraria a chamada para o modelo de IA e validação da resposta
            # Por enquanto, validamos que a estrutura está correta
            if not all(key in case for key in ['prompt', 'expected_category']):
                print(f"Error: Invalid case structure for {case['id']}")
                sys.exit(1)

        print("Evaluation baseline PASSED.")
    except Exception as e:
        print(f"Evaluation FAILED: {e}")
        sys.exit(1)

if __name__ == "__main__":
    run_eval()
