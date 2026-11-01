// Toolchain da Task 121 (bibliotecas do LastWave: kyant0:backdrop 2.0.1 +
// shapes 1.2.1 + Compose 1.12 — o trio EXIGE compileSdk 37 e AGP 9.1+,
// provado pelo AAR metadata e pelo build do LastWave):
//   AGP 8.5.2 → 9.1.1  (o mesmo do LastWave; com android.builtInKotlin=false
//     + android.newDsl=false o KGP classico kotlin.android continua funcionando)
//   Kotlin 1.9.24 → 2.4.10  (a stdlib do backdrop é 2.4.10 — metadata não
//     retrocede: consumidor mais velho não lê classe de versão mais nova)
//   Gradle 8.7 → 9.3.1  (o par provado do LastWave com AGP 9.1.1)
//   + plugin org.jetbrains.kotlin.plugin.compose (obrigatório no Kotlin 2.x)
plugins {
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}
