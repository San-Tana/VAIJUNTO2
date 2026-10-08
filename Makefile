IMAGE = vaijunto-server
CONTAINER = vaijunto-server
PORT = 5000

build:
	docker build -t $(IMAGE) .

run:
	docker run --name $(CONTAINER) -p $(PORT):$(PORT) $(IMAGE)

stop:
	docker stop $(CONTAINER)

remove:
	docker rm $(CONTAINER)

clean:
	docker rm -f $(CONTAINER)

rebuild:
	docker build -t $(IMAGE) .
	docker rm -f $(CONTAINER) 2>/dev/null || true
	docker run --name $(CONTAINER) -p $(PORT):$(PORT) $(IMAGE)