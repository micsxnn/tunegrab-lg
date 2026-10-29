# TuneGrab Liquid Glass

O mesmo TuneGrab que você conhece, vestido de iOS. Fork visual experimental
onde a interface inteira segue o design Liquid Glass da Apple: vidro fosco
de verdade, aberração cromática nas bordas, squircles, menus que abrem por
cima da página e física de mola em cada toque. Continua sendo Android na
alma: é um APK, minSdk 24, o motor yt-dlp embutido é o mesmo do app oficial.

## O que mudou do app original

| Elemento | Antes | Liquid Glass |
|---|---|---|
| Barra de navegação | ícones soltos no fundo | pílula de vidro fosco que desfoca o conteúdo ao passar por trás |
| Menus de item | PopupMenu do Android | context menu iOS: a página recede, desfoca e o card de vidro brota no ponto do toque |
| Bordas de botões e cards | stroke cinza | aberração cromática (vermelho quente de um lado, ciano frio do outro) |
| Cantos | arco de círculo | squircle de superelipse (curva contínua, corner smoothing da Apple) |
| Fonte | Poppins | Inter (o lookalike livre do SF Pro) |
| Fundo | marrom quente | OLED quase preto (#050506) |
| Animações | easing | molas (spring) com quique |

## Como testar

Compile o APK e instale ao lado do app oficial: o `applicationId` é
`com.tunegrab.lg`, então os dois convivem no mesmo aparelho pra comparar.

```bash
./gradlew assembleRelease
```

O APK sai em `app/build/outputs/apk/release/`, assinado com o mesmo keystore
do TuneGrab. A CI deste repo gera o artifact em todo push.

## Base técnica

- GlassView: captura o conteúdo da tela a cada frame, reduz 8x, desfoca
  (RenderEffect no Android 12+, RenderScript no 7-11) e desenha atrás da
  pílula. Blur óptico real, sem bitmap estático.
- GlassPaintDrawable: fill translúcido + halo especular + stroke de
  aberração cromática + hairline, tudo por código.
- Squircle: superelipse |x/5|^n + |y/5|^n = 1 por segmentos, com outline
  provider pra recortar o conteúdo.
- IosContextMenu: overlay que escala a página pra 0.94, arredonda os cantos,
  desfoca o fundo e apresenta o menu com mola no ponto do toque.
- Springs: dynamicanimation com dampingRatio 0.72 reproduzindo o spring do
  UIKit.

## Créditos e licenças

Todo o motor (download, extração, PoToken, player) é o TuneGrab original de
@micaelsan. Este fork só troca a roupa. A fonte Inter é de Rasmus Andersson,
licença OFL. GPL-3.0, igual ao app de origem.
