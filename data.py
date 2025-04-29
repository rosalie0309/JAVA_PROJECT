import re
import csv

input_file = 'resultats.txt'
output_file = 'resultats.csv'

# Regex
pattern_file = re.compile(r'enregistré sous : Downloads/[a-f0-9-]+_([^.]+)\.txt')
pattern_time = re.compile(r'\[METRICS\] TEMPS=(\d+)')
pattern_coupure = re.compile(r'\[METRICS\] COUPURE_CLIENT=(\d+)')

data = []
current_entry = None

with open(input_file, 'r', encoding='utf-8') as file:
    for line in file:
        line = line.strip()

        match_file = pattern_file.search(line)
        if match_file:
            # Sauvegarder l'entrée précédente si elle existe
            if current_entry:
                data.append(current_entry)
            # Nouvelle entrée
            current_entry = {
                'file_name': match_file.group(1),
                'time': '',
                'coupure_client': ''
            }

        elif current_entry:  # Associer les métriques à l'entrée courante
            match_time = pattern_time.search(line)
            if match_time:
                current_entry['time'] = match_time.group(1)

            match_coupure = pattern_coupure.search(line)
            if match_coupure:
                current_entry['coupure_client'] = match_coupure.group(1)

# Ajouter la dernière entrée
if current_entry:
    data.append(current_entry)

# Écriture CSV
with open(output_file, 'w', newline='', encoding='utf-8') as csvfile:
    writer = csv.DictWriter(csvfile, fieldnames=['file_name', 'time', 'coupure_client'])
    writer.writeheader()
    writer.writerows(data)

print(f"{len(data)} lignes écrites dans {output_file}")
