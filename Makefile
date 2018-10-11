tag := $(shell git tag --points-at HEAD )

ifdef tag
else
  tag := $(shell git describe --abbrev=0 --tags)-debug
endif

.PHONY: package
package: *
	gradle jar
	docker build -t moshloop/stateless-jenkins:$(tag) ./
	docker login -u $(USER) -p $(PASS)
	docker push moshloop/stateless-jenkins:$(tag)
