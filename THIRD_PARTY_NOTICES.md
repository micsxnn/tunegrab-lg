# Avisos de terceiros

## Liquid Glass (motor de vidro)

Os arquivos sob `app/src/main/java/dev/liquidglass/` são vendidos do projeto
[LiquidGlass for Android](https://github.com/Abdullajon1881/LiquidGlass)
(módulos `liquidglass-core` e `liquidglass-view`), Copyright dos respectivos
autores, licenciados sob a **Apache License 2.0**. Uma cópia da licença está
no arquivo [LICENSE](LICENSE) deste repositório.

O motor fornece o material Liquid Glass do iOS 26 para o sistema de Views
clássico do Android, com degradação em três níveis:

- `SHADER` (Android 13 / API 33+): shader AGSL com refração de borda,
  dispersão cromática (a aberração cromática), rim especular, grain e
  merge de formas;
- `BLUR` (Android 12 / API 31-32): blur + saturação via `RenderEffect`,
  forma clipada e rim desenhado;
- `SCRIM` (Android 5+): scrim translúcido + rim (fallback universal).

Alterações locais: nenhuma além da cópia dos fontes para embutir no APK.
