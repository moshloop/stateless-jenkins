tag := $(shell git tag --points-at HEAD )

ifdef tag
else
  tag := latest
endif

.PHONY: package
package: *
	gradle jar
	docker build -t moshloop/stateless-jenkins:$(tag) ./
	docker login -u $(USER) -p $(PASS)
	docker push moshloop/stateless-jenkins:$(tag)
	docker tag  moshloop/stateless-jenkins:$(tag) moshloop/stateless-jenkins:latest
	docker push moshloop/stateless-jenkins:latest
