import json
import math
import os

# Production thresholds from ScanRepository.kt
SEMANTIC_SIMILARITY_THRESHOLD = 0.92
PHASH_DISTANCE_THRESHOLD = 5

def hamming_distance(ch1, ch2):
    if len(ch1) != len(ch2):
        return 999
    return sum(c1 != c2 for c1, c2 in zip(ch1, ch2))

def cosine_similarity(v1, v2):
    dot_product = sum(a * b for a, b in zip(v1, v2))
    magnitude1 = math.sqrt(sum(a * a for a in v1))
    magnitude2 = math.sqrt(sum(b * b for b in v2))
    if magnitude1 == 0 or magnitude2 == 0:
        return 0
    return dot_product / (magnitude1 * magnitude2)

def build_groups(items):
    # 1. Exact MD5
    exact_groups = []
    md5_map = {}
    for item in items:
        md5_map.setdefault(item['md5'], []).append(item)

    grouped_ids = set()
    for md5, bucket in md5_map.items():
        if len(bucket) > 1:
            exact_groups.append({'strategy': 'EXACT_MD5', 'items': bucket})
            for item in bucket:
                grouped_ids.add(item['id'])

    remaining = [item for item in items if item['id'] not in grouped_ids]

    # 2. Perceptual pHash
    perceptual_groups = []
    visited_phash = set()
    for i, item in enumerate(remaining):
        if item['id'] in visited_phash:
            continue

        bucket = [item]
        for j in range(i + 1, len(remaining)):
            candidate = remaining[j]
            if candidate['id'] in visited_phash:
                continue

            if hamming_distance(item['phash'], candidate['phash']) <= PHASH_DISTANCE_THRESHOLD:
                bucket.append(candidate)

        if len(bucket) > 1:
            perceptual_groups.append({'strategy': 'PERCEPTUAL_PHASH', 'items': bucket})
            for b in bucket:
                visited_phash.add(b['id'])

    remaining = [item for item in remaining if item['id'] not in visited_phash]

    # 3. Semantic Embedding
    semantic_groups = []
    visited_semantic = set()
    for i, item in enumerate(remaining):
        if item['id'] in visited_semantic:
            continue

        bucket = [item]
        for j in range(i + 1, len(remaining)):
            candidate = remaining[j]
            if candidate['id'] in visited_semantic:
                continue

            if cosine_similarity(item['embedding'], candidate['embedding']) >= SEMANTIC_SIMILARITY_THRESHOLD:
                bucket.append(candidate)

        if len(bucket) > 1:
            semantic_groups.append({'strategy': 'SEMANTIC_EMBED', 'items': bucket})
            for b in bucket:
                visited_semantic.add(b['id'])

    return exact_groups + perceptual_groups + semantic_groups

def evaluate():
    with open('harness/quality_dataset.json', 'r') as f:
        dataset = json.load(f)

    items = dataset['test_cases']
    detected_groups = build_groups(items)

    # Build Ground Truth Pairs
    gt_pairs = set()
    for i, item1 in enumerate(items):
        for j in range(i + 1, len(items)):
            item2 = items[j]
            if item1['group'] and item1['group'] == item2['group']:
                gt_pairs.add(tuple(sorted((item1['id'], item2['id']))))

    # Build Detected Pairs
    detected_pairs = set()
    for group in detected_groups:
        group_items = group['items']
        for i, item1 in enumerate(group_items):
            for j in range(i + 1, len(group_items)):
                item2 = group_items[j]
                detected_pairs.add(tuple(sorted((item1['id'], item2['id']))))

    tp = len(detected_pairs & gt_pairs)
    fp = len(detected_pairs - gt_pairs)
    fn = len(gt_pairs - detected_pairs)

    precision = tp / (tp + fp) if (tp + fp) > 0 else 0
    recall = tp / (tp + fn) if (tp + fn) > 0 else 0
    f1 = 2 * (precision * recall) / (precision + recall) if (precision + recall) > 0 else 0

    report = f"""# Relatório de Qualidade do Pipeline de Detecção de Duplicados

## Configuração Atual
- **pHash Distance Threshold:** {PHASH_DISTANCE_THRESHOLD}
- **Semantic Similarity Threshold:** {SEMANTIC_SIMILARITY_THRESHOLD}

## Métricas de Performance
- **Precision:** {precision:.4f}
- **Recall:** {recall:.4f}
- **F1 Score:** {f1:.4f}
- **False Positives (FP):** {fp}
- **False Negatives (FN):** {fn}

## Detalhamento de Grupos
### Grupos Corretos (TP)
{tp} pares detectados corretamente.

### Falsos Positivos (FP)
{fp} pares detectados incorretamente.
"""
    if fp > 0:
        for p1, p2 in (detected_pairs - gt_pairs):
            report += f"- {p1} <-> {p2} (Deveriam ser distintos)\n"

    report += "\n### Falsos Negativos (FN)\n"
    report += f"{fn} pares não detectados.\n"
    if fn > 0:
        for p1, p2 in (gt_pairs - detected_pairs):
            report += f"- {p1} <-> {p2} (Deveriam estar no mesmo grupo)\n"

    report += """
## Recomendações de Ajuste
1. O threshold de pHash (5) é conservador. Imagens comprimidas são bem capturadas.
2. O impacto do embedding é crucial para selfies e memes onde o pHash falha devido a variações sutis no fundo ou texto.
3. Se o Recall estiver baixo em selfies, considere reduzir o SEMANTIC_SIMILARITY_THRESHOLD para 0.90.
4. Se falsos positivos surgirem em imagens visualmente parecidas mas distintas (ex: fotos diferentes do mesmo objeto), considere aumentar o SEMANTIC_SIMILARITY_THRESHOLD para 0.94.
"""

    os.makedirs('docs', exist_ok=True)
    with open('docs/pipeline_quality_baseline.md', 'w') as f:
        f.write(report)

    print("Avaliação concluída. Relatório gerado em docs/pipeline_quality_baseline.md")

if __name__ == "__main__":
    evaluate()
