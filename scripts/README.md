# Shell scripts

| Script | Purpose |
|---|---|
| `fresh-ingest.sh` | Wipes Neo4j RAG data and re-ingests everything from scratch. Use for first-time setup or when you want a clean slate. |
| `append-ingest.sh` | Re-ingests without clearing existing data. Use when you've added new URLs or directories. Comment out already-ingested items in your profile to avoid re-processing them. |
| `shell.sh` | Runs the application in interactive shell mode. |

Both ingestion scripts start Neo4j in Docker, load your personal profile, and print an **INGESTION COMPLETE** banner when done.

## Personal profiles

Both scripts read `GUIDE_PROFILE` from `.env` (default: `user`).
Each developer can have their own Spring profile:

```bash
cp scripts/user-config/application-user.yml.example scripts/user-config/application-yourname.yml
# Edit to taste, then:
echo 'GUIDE_PROFILE=yourname' >> .env
./scripts/fresh-ingest.sh
```

This loads `application-yourname.yml` with your URLs, directories, and settings.
See `scripts/user-config/README.md` for full details.

## Using append-ingest.sh

Since `append-ingest.sh` doesn't clear the store, you should comment out URLs and directories that are already ingested in your profile to avoid re-processing them. For example:

```yaml
guide:
  urls:
    # - https://docs.embabel.com/embabel-agent/guide/0.3.5-SNAPSHOT/  # already ingested
    - https://some-new-url.com  # new, will be ingested
  directories:
    # - ~/github/jmjava/guide  # already ingested
    - ~/github/jmjava/new-repo  # new, will be ingested
```

Then run `./scripts/append-ingest.sh`. The new content is added alongside existing data in Neo4j.

## Tips

- **If ingestion seems stuck** on a URL: the thread is blocked on fetch -> parse -> embed. Try lowering `embedding-batch-size` to 20, or temporarily remove the slow URL.
- **Speed up ingestion**: increase `embedding-batch-size` (default 50) or `max-chunk-size` (default 4000).
