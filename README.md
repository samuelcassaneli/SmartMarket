# SmartMarket - Comparador de Preços de Mercados com OCR Inteligente Offline

Aplicativo Android nativo construído com **Kotlin**, **Jetpack Compose**, **Room Database** e **Google ML Kit Text Recognition** para processamento OCR de encartes de supermercado 100% offline.

## Funcionalidades Principais
1. **Listas de Compras Dinâmicas**: Criação de listas com controle de itens, quantidades e meses de referência.
2. **Comparador Automático de Mercados**:
   - Calcula o custo total da lista em cada supermercado cadastrado com base nos preços do mês selecionado.
   - Aponta o mercado mais barato, percentual de economia e detalha itens faltantes ou com preços desatualizados.
3. **Alimentação Manual de Preços**:
   - Atualização rápida de preços por produto, mercado e mês/ano.
4. **OCR Inteligente Nativo Offline (ML Kit)**:
   - Suporte a fotos de encartes (câmera/galeria) e arquivos **PDF**.
   - Converte PDFs em Bitmaps via `PdfRenderer` do Android.
   - Reconhecimento de texto e blocos espaciais.
   - Extração heurística de preços (formato brasileiro `R$ XX,XX`).
   - **Fuzzy Matching** (similaridade de texto / Levenshtein) para casar os textos do encarte com os produtos cadastrados na lista.
   - **Tela de Confirmação Rápida**: interface para o usuário revisar, confirmar ou ajustar preços antes de gravar no banco de dados.

## Estrutura do Projeto
- `app/src/main/java/com/smartmarket/`
  - `data/`: Entidades Room, DAOs e Database (`Product`, `Supermarket`, `ProductPrice`, `ShoppingList`, `ShoppingListItem`).
  - `domain/`: Regras de negócio, algoritmo de Fuzzy Matching (`LevenshteinMatcher`), comparador de preços.
  - `ocr/`: Processador ML Kit Text Recognition, parser de encarte e extrator de PDF.
  - `ui/`: Telas em Jetpack Compose (Listas, Comparativo de Mercados, Leitor OCR, Gerenciador de Preços) e Componentes Material 3.
