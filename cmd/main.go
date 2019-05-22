package main

import (
	"context"
	"fmt"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
	"log"
	"math"
	"net"
	"os"
	"time"

	"google.golang.org/grpc"

	"github.com/ahappypie/unica/pkg/api"

	"google.golang.org/grpc/reflection"
)

const (
	port = ":50001"

	epoch = uint64(1546300800000)

	deploymentIdBits = uint64(10)
	maxDeploymentId  = int64(-1) ^ (int64(-1) << deploymentIdBits)
	sequenceBits     = uint64(12)

	deploymentIdShift = sequenceBits
	timestampShift    = sequenceBits + deploymentIdBits
	sequenceMask      = uint64(int64(-1) ^ (int64(-1) << sequenceBits))

	did = uint64(0)
)

var sequence = uint64(0)
var lastTimestamp = uint64(0)

type Server struct{}

func (s *Server) GetId(ctx context.Context, in *unica.UnicaRequest) (*unica.UnicaResponse, error) {
	id, err := generate()
	return &unica.UnicaResponse{Id: id}, err
}

func main() {
	if os.Getenv("dev") == "true" {
		logger, lerr := zap.NewDevelopment()
		if lerr != nil {
			log.Fatalf("can't initialize zap logger: %v", lerr)
		}
		defer logger.Sync()
		zap.ReplaceGlobals(logger)
	} else {
		cfg := zap.Config{
			Encoding:         "json",
			Level:            zap.NewAtomicLevelAt(zapcore.DebugLevel),
			OutputPaths:      []string{"stdout"},
			ErrorOutputPaths: []string{"stderr"},
			EncoderConfig: zapcore.EncoderConfig{
				MessageKey: "message",

				LevelKey:    "level",
				EncodeLevel: zapcore.CapitalLevelEncoder,

				TimeKey:    "time",
				EncodeTime: zapcore.ISO8601TimeEncoder,

				CallerKey:    "caller",
				EncodeCaller: zapcore.ShortCallerEncoder,
			},
		}
		logger, lerr := cfg.Build()
		if lerr != nil {
			log.Fatalf("can't initialize zap logger: %v", lerr)
		}
		defer logger.Sync()
		zap.ReplaceGlobals(logger)
	}
	lis, err := net.Listen("tcp", port)
	if err != nil {
		zap.L().Fatal("falied to bind", zap.Error(err))
	}
	s := grpc.NewServer()
	unica.RegisterIdServiceServer(s, &Server{})
	reflection.Register(s)
	err2 := s.Serve(lis)
	if err2 != nil {
		zap.L().Fatal("failed to serve", zap.Error(err2))
	}
	zap.L().Info("serving on", zap.String("port", port))
}

func generate() (uint64, error) {
	var timestamp = makeTimestamp()

	if timestamp < lastTimestamp {
		zap.L().Warn("clock is moving backwards", zap.Uint64("wait", lastTimestamp-timestamp))
		timestamp = holdUntil(lastTimestamp)
	}

	if lastTimestamp == timestamp {
		sequence = (sequence + 1) & sequenceMask
		if sequence > (1 << sequenceBits) {
			zap.L().Warn("sequence overloaded", zap.Uint64("sequence", sequence))
			return 0, fmt.Errorf("sequence overloaded at %v, try again later", sequence)
		} else if sequence == 0 {
			timestamp = holdUntil(lastTimestamp)
		}
	} else {
		sequence = 0
	}

	lastTimestamp = timestamp

	x := ((timestamp - epoch) << timestampShift) |
		(did << deploymentIdShift) |
		sequence

	zap.L().Debug("components", zap.Uint64("epoch", epoch),
		zap.Uint64("timestamp", timestamp),
		zap.Uint64("timestampShift", timestampShift),
		zap.Uint64("deploymentId", did),
		zap.Uint64("deploymentIdShift", deploymentIdShift),
		zap.Uint64("sequence", sequence),
		zap.Uint64("id", x),
		zap.Uint8("idBits", uint8(math.Round(math.Log2(float64(x))))))

	return x, nil
}

func holdUntil(ts uint64) uint64 {
	var timestamp = makeTimestamp()
	for timestamp <= ts {
		timestamp = makeTimestamp()
	}
	return timestamp
}

func makeTimestamp() uint64 {
	return uint64(time.Now().UnixNano() / 1e6)
}
