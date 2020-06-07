#!/bin/bash

EXP_ID=$1
DIM_VALUE=$2
INSTANCES=$3
PARTITIONS=${4:-40}
CPU_LIMIT=${5:-1000m}
MEMORY_LIMIT=${6:-4Gi}
KAFKA_STREAMS_COMMIT_INTERVAL_MS=${7:-100}
EXECUTION_MINUTES=${8:-5}

echo "EXP_ID: $EXP_ID"
echo "DIM_VALUE: $DIM_VALUE"
echo "INSTANCES: $INSTANCES"
echo "PARTITIONS: $PARTITIONS"
echo "CPU_LIMIT: $CPU_LIMIT"
echo "MEMORY_LIMIT: $MEMORY_LIMIT"
echo "KAFKA_STREAMS_COMMIT_INTERVAL_MS: $KAFKA_STREAMS_COMMIT_INTERVAL_MS"
echo "EXECUTION_MINUTES: $EXECUTION_MINUTES"

# Create Topics
#PARTITIONS=40
#kubectl run temp-kafka --rm --attach --restart=Never --image=solsson/kafka --command -- bash -c "./bin/kafka-topics.sh --zookeeper my-confluent-cp-zookeeper:2181 --create --topic input --partitions $PARTITIONS --replication-factor 1; ./bin/kafka-topics.sh --zookeeper my-confluent-cp-zookeeper:2181 --create --topic configuration --partitions 1 --replication-factor 1; ./bin/kafka-topics.sh --zookeeper my-confluent-cp-zookeeper:2181 --create --topic output --partitions $PARTITIONS --replication-factor 1"
PARTITIONS=$PARTITIONS
kubectl exec kafka-client -- bash -c "kafka-topics --zookeeper my-confluent-cp-zookeeper:2181 --create --topic input --partitions $PARTITIONS --replication-factor 1; kafka-topics --zookeeper my-confluent-cp-zookeeper:2181 --create --topic configuration --partitions 1 --replication-factor 1; kafka-topics --zookeeper my-confluent-cp-zookeeper:2181 --create --topic output --partitions $PARTITIONS --replication-factor 1"

# Start workload generator
NUM_SENSORS=$DIM_VALUE
WL_MAX_RECORDS=150000
WL_INSTANCES=$(((NUM_SENSORS + (WL_MAX_RECORDS -1 ))/ WL_MAX_RECORDS))

cat <<EOF >uc1-workload-generator/set_paramters.yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: titan-ccp-load-generator
spec:
  replicas: $WL_INSTANCES
  template:
    spec:
      containers:
      - name: workload-generator
        env:
        - name: NUM_SENSORS
          value: $NUM_SENSORS
        - name: INSTANCES
          value: $WL_INSTANCES
EOF
kubectl apply -k uc1-workload-generator

# Start application
REPLICAS=$INSTANCES
cat <<EOF >uc1-application/set_paramters.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: titan-ccp-aggregation
spec:
  template:
    spec:
      containers:
      - name: uc1-application
        env:
        - name: COMMIT_INTERVAL_MS
          value: $KAFKA_STREAMS_COMMIT_INTERVAL_MS
        resources:
          limits:
            memory: $MEMORY_LIMIT
            cpu: $CPU_LIMIT
EOF
kubectl apply -k uc1-application
kubectl scale deployment titan-ccp-aggregation --replicas=$REPLICAS

# Execute for certain time
sleep ${EXECUTION_MINUTES}m

# Run eval script
source ../.venv/bin/activate
python lag_analysis.py $EXP_ID uc1 $DIM_VALUE $INSTANCES $EXECUTION_MINUTES
deactivate

# Stop workload generator and app
kubectl delete -k uc1-workload-generator
kubectl delete -k uc1-application


# Delete topics instead of Kafka
#kubectl exec kafka-client -- bash -c "kafka-topics --zookeeper my-confluent-cp-zookeeper:2181 --delete --topic 'input,output,configuration,titan-.*'"
# kafka-topics --zookeeper my-confluent-cp-zookeeper:2181 --delete --topic '.*'
#sleep 30s # TODO check
#kubectl exec kafka-client -- bash -c "kafka-topics --zookeeper my-confluent-cp-zookeeper:2181 --list" | sed -n '/^titan-.*/p;/^input$/p;/^output$/p;/^configuration$/p'
#kubectl exec kafka-client -- bash -c "kafka-topics --zookeeper my-confluent-cp-zookeeper:2181 --list" | sed -n '/^titan-.*/p;/^input$/p;/^output$/p;/^configuration$/p' | wc -l
#kubectl exec kafka-client -- bash -c "kafka-topics --zookeeper my-confluent-cp-zookeeper:2181 --list"

#kubectl exec kafka-client -- bash -c "kafka-topics --zookeeper my-confluent-cp-zookeeper:2181 --delete --topic 'input,output,configuration,titan-.*'"
echo "Finished execution, print topics:"
#kubectl exec kafka-client -- bash -c "kafka-topics --zookeeper my-confluent-cp-zookeeper:2181 --list" | sed -n -E '/^(titan-.*|input|output|configuration)( - marked for deletion)?$/p'
while test $(kubectl exec kafka-client -- bash -c "kafka-topics --zookeeper my-confluent-cp-zookeeper:2181 --list" | sed -n -E '/^(titan-.*|input|output|configuration)( - marked for deletion)?$/p' | wc -l) -gt 0
do
    kubectl exec kafka-client -- bash -c "kafka-topics --zookeeper my-confluent-cp-zookeeper:2181 --delete --topic 'input|output|configuration|titan-.*'"
    echo "Wait for topic deletion"
    sleep 5s
    #echo "Finished waiting, print topics:"
    #kubectl exec kafka-client -- bash -c "kafka-topics --zookeeper my-confluent-cp-zookeeper:2181 --list" | sed -n -E '/^(titan-.*|input|output|configuration)( - marked for deletion)?$/p'
    # Sometimes a second deletion seems to be required
done
echo "Finish topic deletion, print topics:"
#kubectl exec kafka-client -- bash -c "kafka-topics --zookeeper my-confluent-cp-zookeeper:2181 --list" | sed -n -E '/^(titan-.*|input|output|configuration)( - marked for deletion)?$/p'
echo "Exiting script"

KAFKA_LAG_EXPORTER_POD=$(kubectl get pod -l app.kubernetes.io/name=kafka-lag-exporter -o jsonpath="{.items[0].metadata.name}")
kubectl delete pod $KAFKA_LAG_EXPORTER_POD
