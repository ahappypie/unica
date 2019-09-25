FROM golang:1.13.0-alpine3.10 as builder

RUN set -eux; \
    apk add --no-cache --virtual git

ENV GOPATH /go/
ENV GO111MODULE=on

WORKDIR $GOPATH/src/github.com/ahappypie/unica


ADD go.mod go.sum ./
RUN go mod download

ADD api/ api/
ADD cmd/ cmd/
ADD pkg/ pkg/

RUN CGO_ENABLED=0 go build -a -o /go/bin/unica cmd/main.go

FROM alpine:3.10

RUN set -eux; \
    apk add --no-cache --virtual ca-certificates

COPY --from=builder /go/bin/unica /go/bin/unica

ENTRYPOINT ["/go/bin/unica"]

EXPOSE 50001