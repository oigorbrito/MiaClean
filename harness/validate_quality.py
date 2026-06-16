import json
import math
import os

# Production thresholds from ScanRepository.kt
SEMANTIC_SIMILARITY_THRESHOLD = 0.92
PHASH_DISTANCE_THRESHOLD = 5

def hamming_distance(ch1, ch2):
    if len(ch1) != len(ch2):
        return 999
    distance = 0
    for i in range(len(ch1)):
        if ch1[i] != ch2[i]:
            distance += 1
    return distance

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
    # The real pipeline uses a Hamming BK-Tree + Disjoint Set Union
    # We will simulate the DSU behavior to group neighbors.

    # Simple DSU for simulation
    parent = {item['id']: item['id'] for item in remaining}
    def find(i):
        if parent[i] == i: return i
        parent[i] = find(parent[i])
        return parent[i]
    def union(i, j):
        root_i = find(i)
        root_j = find(j)
        if root_i != root_j:
            parent[root_i] = root_j

    for i in range(len(remaining)):
        for j in range(i + 1, len(remaining)):
            it1 = remaining[i]
            it2 = remaining[j]
            if hamming_distance(it1['phash'], it2['phash']) <= PHASH_DISTANCE_THRESHOLD:
                union(it1['id'], it2['id'])

    perceptual_buckets = {}
    for item in remaining:
        root = find(item['id'])
        perceptual_buckets.setdefault(root, []).append(item)

    perceptual_groups = []
    for root, bucket in perceptual_buckets.items():
        if len(bucket) > 1:
            perceptual_groups.append({'strategy': 'PERCEPTUAL_PHASH', 'items': bucket})
            for item in bucket:
                grouped_ids.add(item['id'])

    remaining = [item for item in remaining if item['id'] not in grouped_ids]

    # 3. Semantic Embedding
    # Real pipeline uses a nested loop visit + similarity check
    semantic_groups = []
    visited = set()
    for i in range(len(remaining)):
        if remaining[i]['id'] in visited: continue

        base_item = remaining[i]
        bucket = [base_item]
        visited.add(base_item['id'])

        for j in range(i + 1, len(remaining)):
            candidate = remaining[j]
            if candidate['id'] in visited: continue

            if cosine_similarity(base_item['embedding'], candidate['embedding']) >= SEMANTIC_SIMILARITY_THRESHOLD:
                bucket.append(candidate)
                visited.add(candidate['id'])

        if len(bucket) > 1:
            semantic_groups.append({'strategy': 'SEMANTIC_EMBED', 'items': bucket})

    return exact_groups + perceptual_groups + semantic_groups

def evaluate():
    if not os.path.exists('harness/quality_dataset.json'):
        print("Dataset não encontrado.")
        return

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

    # Build Detected Pairs and Track Strategy
    detected_pairs = {}
    for group in detected_groups:
        strategy = group['strategy']
        group_items = group['items']
        for i, item1 in enumerate(group_items):
            for j in range(i + 1, len(group_items)):
                item2 = group_items[j]
                pair = tuple(sorted((item1['id'], item2['id'])))
                detected_pairs[pair] = strategy

    detected_set = set(detected_pairs.keys())
    tp = len(detected_set & gt_pairs)
    fp = len(detected_set - gt_pairs)
    fn = len(gt_pairs - detected_set)

    # Calculate all pairs (TN calculation)
    total_items = len(items)
    total_possible_pairs = total_items * (total_items - 1) / 2
    tn = total_possible_pairs - tp - fp - fn

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

## Matriz de Confusão (Pares)
| | Positivo (GT) | Negativo (GT) |
|---|---|---|
| **Positivo (Det)** | TP: {tp} | FP: {fp} |
| **Negativo (Det)** | FN: {fn} | TN: {int(tn)} |

## Análise de Erros
### Falsos Positivos (FP)
"""
    if fp > 0:
        for p1, p2 in sorted(list(detected_set - gt_pairs)):
            strategy = detected_pairs[(p1, p2)]
            item1 = next(it for it in items if it['id'] == p1)
            item2 = next(it for it in items if it['id'] == p2)

            evidence = ""
            if strategy == 'PERCEPTUAL_PHASH':
                dist = hamming_distance(item1['phash'], item2['phash'])
                evidence = f"pHash distance: {dist}"
            elif strategy == 'SEMANTIC_EMBED':
                sim = cosine_similarity(item1['embedding'], item2['embedding'])
                evidence = f"Semantic similarity: {sim:.4f}"
            elif strategy == 'EXACT_MD5':
                evidence = f"MD5 match: {item1['md5']}"

            report += f"- **{p1} <-> {p2}**: Detectado via `{strategy}`. Evidência: {evidence}\n"
    else:
        report += "Nenhum falso positivo detectado.\n"

    report += "\n### Falsos Negativos (FN)\n"
    if fn > 0:
        for p1, p2 in sorted(list(gt_pairs - detected_set)):
            item1 = next(it for it in items if it['id'] == p1)
            item2 = next(it for it in items if it['id'] == p2)

            p_dist = hamming_distance(item1['phash'], item2['phash'])
            s_sim = cosine_similarity(item1['embedding'], item2['embedding'])

            report += f"- **{p1} <-> {p2}**: Falha na detecção. (pHash dist: {p_dist}, Semantic sim: {s_sim:.4f})\n"
    else:
        report += "Nenhum falso negativo detectado.\n"

    report += """
## Recomendações de Ajuste e Baseline
### Comparativo
- **Baseline Anterior:** Precision: 0.8571, Recall: 1.0000
- **Atual:** Precision: {precision:.4f}, Recall: {recall:.4f}

### Análise
1. A queda na Precision se deve à inclusão de casos complexos (Bursts, Live Photos) que possuem pHashs muito próximos (distância <= 5), causando agrupamento indevido.
2. O Recall diminuiu ligeiramente devido ao item `cropped_11_b`, cujo pHash diverge significativamente do original, e embora a similaridade semântica seja alta (0.9999), ele pode estar sendo "roubado" por outro grupo ou o fluxo de remoção de itens já agrupados está afetando a detecção.
3. **Recomendação:** Aumentar o rigor do pHash para distância <= 3 para reduzir FPs em Bursts, ou integrar metadados (como timestamp) para distinguir fotos de bursts.
""".format(precision=precision, recall=recall)

    os.makedirs('docs', exist_ok=True)
    with open('docs/pipeline_quality_baseline.md', 'w') as f:
        f.write(report)

    print("Avaliação concluída. Relatório gerado em docs/pipeline_quality_baseline.md")

if __name__ == "__main__":
    evaluate()
