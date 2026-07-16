# Station Finder Discord Bot

日本の駅検索と乗換案内を行うDiscord Botです。Transit APIのJSONレスポンスをJacksonでJavaの型へ変換して利用します。

## コマンド

- `/ping` — Botの応答確認
- `/station name:東京` — 駅候補、読み、路線を表示（駅名入力の候補表示に対応）
- `/route from:東京 to:新宿` — 現在時刻から2駅間の経路を表示
- `/route from:東京 to:新宿 date:2026-07-20 time:09:00 search:到着` — 日時と検索種別を指定
- `/departures station:東京` — 選択した駅・路線の発車案内を表示

経路検索では、出発・到着時刻、所要時間、乗換回数、乗換駅、路線、列車種別、行先、番線、APIが提供する場合は運賃を最大3候補まで表示します。

`/route`では、最大乗換回数、鉄道のみ、徒歩を避ける条件も指定できます。駅候補は10分間・最大100検索語だけメモリへ保存し、API通信は同時4件までに制限してRaspberry Piの負荷を抑えます。永続データベースは使用しません。

## 起動

Discord Botトークンを環境変数に設定してから起動します。

```powershell
$env:DISCORD_TOKEN = "Discord Botのトークン"
.\gradlew.bat run
```

起動時に4つのスラッシュコマンドがDiscordへ自動登録されます。初回登録時はDiscord側へ表示が反映されるまで少し時間がかかる場合があります。

## Raspberry Pi 3B+での実行

64bit版Raspberry Pi OSとJava 21を使用してください。Botは最大ヒープ320MB、Serial GC、JDAの軽量モードで動作し、未使用の音声ライブラリも除外しています。

最初に配布用ファイルを生成します。

```bash
./gradlew --no-daemon installDist
```

その後はGradleを常駐させず、生成された起動スクリプトを直接使用します。

```bash
export DISCORD_TOKEN="Discord Botのトークン"
./app/build/install/app/bin/app
```

メモリの少ない端末では、普段の起動に`./gradlew run`を使わないことでGradle Daemon分のメモリを節約できます。

テストは次のコマンドで実行できます。

```powershell
.\gradlew.bat test
```

APIは認証不要の [Transit API](https://api.transit.ls8h.com/) を利用しています。検索結果はAPIが保持する交通データと検索時刻に依存します。
