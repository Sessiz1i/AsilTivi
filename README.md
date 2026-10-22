# Beleş Tivi Clone — K1-A

Asgari Android/TV debug kabuğu. Ürün ekranı, kullanıcı verisi, ağ/oynatıcı veya kalıcı veri katmanı içermez. T1–T7 görselleri bu pakette entegre edilmedi.

- `shared`: yalnız platform bağımsız debug kimliği; Android hedefli KMP kabuğu.
- `sharedUi`: ortak Compose durumu, Focus A/B, test katmanı ve odak dönüşü.
- `androidApp`: tek telefon/TV launcher, Activity, Android Back adaptörü ve nötr BT Debug kaynakları.

AGP 8.10.1, Kotlin/KMP/Compose compiler 2.2.21, Compose Multiplatform 1.9.3, Activity Compose 1.10.1; JVM17, min23/compile36/target36, Build Tools35.0.0. Yalnız Android hedefi vardır.

Mevcut Gradle 8.11.1 tam yoluyla `:androidApp:assembleDebug :androidApp:lintDebug k1DependencyInventory --write-locks --no-daemon --console=plain` çalıştırılır. Ardından kilit değişikliği olmadan normal `:androidApp:assembleDebug` doğrulanır. Wrapper resmî görevle üretilir; bu teslimin build yöntemi ve gerçek sonuçları ana rapordadır. Wrapper dağıtımı için zaten mevcut ZIP yeniden indirilmedi.

Araç alt süreçleri yalnız proje içi JAVA_HOME, ANDROID_HOME, ANDROID_USER_HOME, ANDROID_PREFS_ROOT ve GRADLE_USER_HOME kullanır. K1-A devamında AGP analytics'in ayrı yol davranışı doğrulandı: `ANDROID_PREFS_ROOT=E:/codexGorev/Beles-Tivi/araclar/android-user-home`, `ANDROID_USER_HOME=E:/codexGorev/Beles-Tivi/araclar/android-user-home/.android`. Analytics ilk yolu doğrudan, SDK ise `.android` alt yolunu kullanır. Bu değerler yalnız child süreçte verilir; kalıcı ortam değişmez. Kanıt alanındaki devam-01/Run-K1Tool.ps1 bu yöntemi uygular. SDK otomatik indirmesi ve JDK otomatik edinimi kapalıdır. Debug signing yolu açıkça `../araclar/android-user-home/debug.keystore`; standart debug geliştirme parolaları üretim sırrı değildir. Anahtar kopyalanmaz veya Git'e eklenmez. Yerel SDK yolu `local.properties` dosyasındadır.

Kilit kapsamı debug compile/runtime ve katkı veren commonMain metadata konfigürasyonlarıdır. Araç üretimli `gradle.lockfile` dosyaları ve `build/reports/k1/locked-configurations.tsv` gerçek kapsamı gösterir. Envanter tüm çözülmüş proje/harici bileşenleri ve bağımlılık kenarlarını kaydeder; artifact dosyası listesi yalnız harici ModuleComponentIdentifier girdilerini kapsar. Android proje modüllerinin lint/manifest/classes ikincil artifact varyantlarını gereksiz yere seçmez. Plugin sürümleri sabittir; plugin/buildscript/compiler/lint araçlarının bütün transitif grafiği bu kilit teslimine dahil değildir. Envanter artifact hash'i yayımlanmış üretici hash'i veya güvenlik denetimi sayılmaz.

Android app debug compile/runtime grafiğinde ortak metadata-only `kotlin-stdlib-common` shim'i, KGP2.2.21'in KMP platform derlemelerinde uyguladığı sınırla aynı biçimde dışlanır. JVM stdlib2.2.21 kilitli kalır; shared/sharedUi ortak metadata grafiği stdlib-common'u korur. Transitif sürüm force, kilit kapatma veya elle lock düzenleme kullanılmaz.

Build/lint/APK denetimi cihazda açılış, odak, Back, dokunma, kumanda, yaşam döngüsü veya görsel sadakat kanıtı değildir. Bu görev APK yüklemez/açmaz ve sonraki paketi başlatmaz.
