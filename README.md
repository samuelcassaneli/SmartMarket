# SmartMarket 2.0

App Android para organizar compras de mercado. Ele lê a nota fiscal pelo QR Code, busca preços nos sites dos mercados, baixa os encartes sozinho e compara onde a lista sai mais barata.

Stack: Kotlin, Jetpack Compose (Material 3), Room, ML Kit (on-device), CameraX, OkHttp/Jsoup e WorkManager.

## Recursos

### Nota fiscal (NFC-e) pelo QR Code
- Escaneie o QR do cupom com a câmera ou escolha uma foto da galeria. Também dá para digitar a chave de acesso de 44 dígitos.
- O app consulta a página pública da SEFAZ (parser afinado para o RJ, com parser genérico para os demais estados) e importa:
  - loja, CNPJ, data e hora;
  - itens com quantidade, unidade, valor unitário e total;
  - descontos e forma de pagamento.
- Se a página pedir captcha, ela abre dentro do app para você resolver e a importação continua.
- Na revisão, cada item é vinculado a um produto do catálogo. Os vínculos que você confirma são aprendidos e valem para as próximas notas.
- Ao salvar, o app registra:
  - a compra, para o histórico e o dashboard;
  - o preço de cada item na data da compra;
  - a quantidade comprada na despensa.
- A mesma nota não é importada duas vezes, porque o controle é feito pela chave de acesso.

### Busca nos mercados (Resende-RJ)
- **Atacadão**: busca de qualquer produto no catálogo online, com preço da loja da sua região (CEP configurável).
- **Spani**: busca online quando disponível, e encarte em PDF baixado automaticamente.
- **Dom Atacadista**: encarte baixado automaticamente (páginas em imagem).
- Cada oferta mostra o preço por kg, L ou unidade e tem as ações "Salvar preço" e "Adicionar à lista".

### Encartes
- Sincronização diária automática, com notificação quando sai encarte novo e quantos itens são da sua lista.
- A leitura automática filtra textos de enfeite e não confunde variantes ("leite condensado" não vira "leite integral").
- Você revisa as ofertas, aceita ou rejeita, e vincula os itens que não foram reconhecidos.
- Também dá para ler um encarte manualmente, por foto ou PDF.

### Listas inteligentes
- Comparação da lista entre todos os mercados, com itens faltando, variação contra o mês anterior e economia.
- Divisão inteligente: sugere quando vale a pena comprar em 2 mercados.
- Modo mercado: checklist grande, com o total corrente contra o orçamento.
- Compartilhamento da lista pelo WhatsApp.

### Dashboard, histórico e alertas
- Gastos do mês contra o orçamento, gráfico de 6 meses, gasto por categoria e por mercado.
- Histórico de preço de cada produto, com gráfico por mercado, menor preço, preço médio e último preço.
- Alertas de preço: avisam quando o produto fica abaixo do preço-alvo ou cai de preço.

### Despensa
- Estoque de casa alimentado pelas notas, com quantidade mínima e validade.
- Gera a lista de reposição com base no consumo.

### Backup
- Exportar e importar tudo em JSON. Você pode salvar no Google Drive pelo seletor de arquivos.
- Backup automático semanal numa pasta que você escolhe.

## Estrutura

```
app/src/main/java/com/smartmarket/
  AppContainer.kt          DI manual: banco, HTTP, repositórios
  data/                    Room (entidades, DAOs, migrações, seed), DataStore, repositórios
  domain/
    nfce/                  chave de acesso, QR Code, consulta e parser da SEFAZ
    online/                busca nos sites dos mercados
    flyers/  ocr/          download e leitura de encartes
    matching/              casamento de textos com produtos + aliases aprendidos
    comparator/            comparação de listas e divisão entre mercados
    alerts/ pantry/ backup/ share/
  work/                    WorkManager: encartes, alertas, backup
  ui/                      tema, componentes, navegação e telas
app/schemas/               schemas do Room (base das migrações)
```

## Build

```
./gradlew assembleDebug        # APK em app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # testes unitários
```

O GitHub Actions compila o APK de debug a cada push na `main` e o publica em Releases.

## Banco de dados
A versão 3 do banco migra os dados da 2 sem perder nada. A migração foi gerada a partir dos schemas exportados em `app/schemas/` e conferida contra o schema final.
