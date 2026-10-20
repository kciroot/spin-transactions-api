# =====================================================================================================
# Transaction Execution API - control panel.
#   NUCLEO (obligatorio, gratis, sin nube):  make run | make test | make up | make demo
#   OPCIONAL (se activa solo si lo pides):   make up-backup | make backup | make k8s-local | make k8s-local-full
# `make help` lista todo. Nada opcional se ejecuta por defecto.
# =====================================================================================================
.DEFAULT_GOAL := help
CLUSTER ?= spin-local
IMAGE   ?= transactions-api:1.0.0
FILE    ?=

.PHONY: help run test verify docker-build up down demo up-backup backup restore k8s-local k8s-local-full k8s-down pre-entrega

help: ## Muestra esta ayuda
	@awk 'BEGIN {FS = ":.*## "} /^[a-zA-Z_-]+:.*## / {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

# ------------------------------- NUCLEO -------------------------------
run: ## [NUCLEO] Levanta la API local (H2 + mock del proveedor embebido). Requiere JDK 17+ y Maven
	mvn spring-boot:run -Dspring-boot.run.profiles=local

test: ## [NUCLEO] Tests unitarios + integracion (Testcontainers se omite si no hay Docker)
	mvn -B test

verify: ## [NUCLEO] Build completo con todos los tests
	mvn -B verify

docker-build: ## [NUCLEO] Construye la imagen Docker
	docker build -t $(IMAGE) .

up: ## [NUCLEO] Docker Compose: PostgreSQL + API (http://localhost:8080)
	docker compose up --build -d
	@echo "API: http://localhost:8080  |  Swagger: http://localhost:8080/swagger-ui.html  |  API key: dev-key-change-me"

down: ## [NUCLEO] Detiene Docker Compose
	docker compose --profile backup down

demo: ## [NUCLEO] Demo guiada con curl contra una instancia en marcha (BASE_URL / API_KEY configurables)
	./scripts/demo.sh

# ------------------------------- OPCIONAL: backups -------------------------------
up-backup: up ## [OPCIONAL] Levanta el nucleo y ademas ejecuta un backup inmediato
	$(MAKE) backup

backup: ## [OPCIONAL] Backup logico verificado a ./backups (requiere `make up` antes)
	docker compose --profile backup run --rm backup

restore: ## [OPCIONAL] Restaura FILE=backups/xxx.dump en la BD 'transactions_restore' (no toca la original)
	@test -n "$(FILE)" || (echo "Uso: make restore FILE=backups/transactions_YYYYMMDDTHHMMSSZ.dump" && exit 1)
	docker compose --profile backup run --rm -e PGDATABASE=transactions_restore --entrypoint /scripts/restore.sh backup /backups/$(notdir $(FILE)) --yes

# ------------------------------- OPCIONAL: Kubernetes local (gratis, sin nube) -------------------------------
k8s-local: ## [OPCIONAL] Cluster local con kind + despliegue nucleo (requiere docker, kind, kubectl)
	@command -v kind >/dev/null    || (echo "Falta kind: https://kind.sigs.k8s.io" && exit 1)
	@command -v kubectl >/dev/null || (echo "Falta kubectl" && exit 1)
	kind get clusters | grep -qx $(CLUSTER) || kind create cluster --name $(CLUSTER)
	docker build -t $(IMAGE) .
	kind load docker-image $(IMAGE) --name $(CLUSTER)
	kubectl apply -k deploy/k8s/overlays/local
	kubectl -n transactions rollout status deploy/transactions-api --timeout=240s
	@echo "Siguiente: kubectl -n transactions port-forward svc/transactions-api 8080:80   (API key: local-k8s-key)"

k8s-local-full: ## [OPCIONAL] Igual + autoescalado, PDB, NetworkPolicy y CronJob de backup
	@command -v kind >/dev/null    || (echo "Falta kind: https://kind.sigs.k8s.io" && exit 1)
	@command -v kubectl >/dev/null || (echo "Falta kubectl" && exit 1)
	kind get clusters | grep -qx $(CLUSTER) || kind create cluster --name $(CLUSTER)
	docker build -t $(IMAGE) .
	kind load docker-image $(IMAGE) --name $(CLUSTER)
	kubectl apply -k deploy/k8s/overlays/local-full
	kubectl -n transactions rollout status deploy/transactions-api --timeout=240s
	@echo "HPA/PDB/NetworkPolicy/CronJob: kubectl -n transactions get hpa,pdb,networkpolicy,cronjob"

k8s-down: ## [OPCIONAL] Elimina el cluster local de kind
	kind delete cluster --name $(CLUSTER)

# ------------------------------- Entrega -------------------------------
pre-entrega: ## Verifica que no queden datos por completar y que todo compile y pase tests
	@if grep -rIn "TODO-RICK" --exclude-dir=.git --exclude-dir=privado --exclude-dir=target --exclude=Makefile . ; then \
	  echo; echo "ERROR: completa los TODO-RICK de arriba antes de entregar (tiempos reales, tu contribucion, etc.)"; exit 1; fi
	mvn -B verify
	@git status --short
	@echo "OK: listo para entregar."
