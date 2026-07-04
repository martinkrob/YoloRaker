# Jak přesunout YoloRaker na jiný server (s Podmanem)

Jelikož jsme vytvořili tzv. **Multi-stage Dockerfile**, je přesun extrémně jednoduchý. Tento Dockerfile totiž obsahuje instrukce, jak si má Podman sám stáhnout Javu a aplikaci si zkompilovat. 

Díky tomu **nepotřebuješ na cílovém serveru instalovat ani Javu, ani Maven**. Stačí tam mít pouze Podman!

Zde je postup krok za krokem:

## Varianta A: Přenos zdrojových kódů (Nejčistší způsob)

Tato varianta je nejlepší. Prostě přeneseš kód na server a server si kontejner sestaví sám.

### Krok 1: Zabalení projektu
Na tvém počítači s Netbeans (kde teď jsi) zabal celou složku projektu do archivu (např. ZIP nebo TAR).
Můžeš vynechat složku `target`, ta zabírá zbytečně místo, protože se vytvoří na serveru znovu. Potřebuješ hlavně:
- složku `src`
- soubor `pom.xml`
- soubor `Dockerfile`
- soubor `compose.yml`

### Krok 2: Přenos na server
Přesuň tento ZIP archiv na svůj cílový server (např. přes `scp`, WinSCP, nebo Flash disk) a tam ho rozbal, třeba do `/opt/yoloraker`.

### Krok 3: Spuštění na serveru
Přihlas se na svůj cílový server přes terminál (SSH), přejdi do složky, kam jsi projekt rozbalil:
```bash
cd /opt/yoloraker
```
A spusť sestavení a zapnutí kontejneru:
```bash
podman-compose up -d --build
```
*Poznámka: Pokud na serveru nemáš `podman-compose`, ale máš čistý `podman`, můžeš to spustit takto:*
```bash
podman build -t yoloraker .
podman run -d --name yoloraker -p 7070:7070 -v ./data:/app/data -e TZ=Europe/Prague yoloraker
```

Hotovo! Server si sám kontejner postaví a YoloRaker poběží.

---

## Varianta B: Přenos hotového JAR souboru (Bez kompilace na serveru)

Pokud nechceš, aby se aplikace kompilovala až na serveru (protože jsi ji už zkompiloval u sebe v Netbeans a máš hotový `.jar` soubor), můžeš udělat toto:

### Krok 1: Úprava Dockerfile
Otevři `Dockerfile` a smaž z něj první část (Krok 1). Nech tam jen toto:
```dockerfile
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN mkdir -p /app/data
# Zkopírujeme JAR z naší složky (nikoliv z buildu)
COPY YoloRaker-1.0.0.jar /app/yoloraker.jar
EXPOSE 7070
ENTRYPOINT ["java", "-jar", "/app/yoloraker.jar"]
```

### Krok 2: Přenos souborů
Na server zkopíruj pouze tyto 3 soubory, vše do jedné složky:
1. `target/YoloRaker-1.0.0.jar` (Z tvého Netbeans počítače)
2. Upravený `Dockerfile`
3. Původní `compose.yml`

### Krok 3: Spuštění
Na cílovém serveru opět přejdi do složky s těmito třemi soubory a spusť:
```bash
podman-compose up -d --build
```
V tomto případě se na serveru už nic nekompiluje, Podman pouze vezme tvůj hotový JAR a rovnou ho spustí.
