# DevInsight — Kubernetes Deployment Guide (Local & AWS EKS)

This guide documents how to deploy the entire DevInsight microservice platform to Kubernetes using production-grade declarative manifests located in [`k8s/`](../k8s).

---

## 1. Architecture Overview in Kubernetes

All workloads run in the dedicated namespace: `devinsight`.

```text
Kubernetes Cluster (Local Docker Desktop Kind / AWS EKS)
  └── Namespace: devinsight
       ├── ConfigMap & Secrets (devinsight-config, devinsight-secrets, postgres-init-script)
       │
       ├── Stateful Storage (PersistentVolumeClaims)
       │    ├── postgres-pvc (2Gi ReadWriteOnce)
       │    └── ollama-pvc (8Gi ReadWriteOnce)
       │
       ├── Infrastructure Layer
       │    ├── postgres (Deployment + ClusterIP Service :5432)
       │    └── ollama (Deployment + ClusterIP Service :11434 + ollama-model-puller Job)
       │
       ├── Application Microservices (Docker Hub images: adityagoku12/devinsight-*)
       │    ├── ml-pipeline (Deployment + ClusterIP Service :8000)
       │    ├── user-service (Deployment + ClusterIP Service :8081)
       │    ├── llm-service (Deployment + ClusterIP Service :8084)
       │    ├── log-layer (Deployment + ClusterIP Service :8082)
       │    ├── api-gateway (Deployment + ClusterIP Service :8080)
       │    └── frontend (Deployment + NodePort Service :80 / NodePort 30080)
```

---

## 2. Deploying Locally (Docker Desktop Kubernetes / Kind)

### Step 1: Deploy All Resources
Run a single command from your repository root:

```bash
kubectl apply -k k8s/
```

### Step 2: Verify Pods & Rollout Status
```bash
# Check all pods in the devinsight namespace:
kubectl get pods -n devinsight

# Watch rollout status in real-time:
kubectl get pods -n devinsight -w
```

### Step 3: Access the Frontend & Application
You can access the frontend in your browser through either method:

#### Method A: Port Forwarding (Recommended for local dev)
```bash
kubectl port-forward svc/frontend -n devinsight 5173:80
```
Then open: **[http://localhost:5173](http://localhost:5173)**

#### Method B: Direct NodePort
Open: **[http://localhost:30080](http://localhost:30080)**

---

## 3. Production Deployment to AWS EKS (Elastic Kubernetes Service)

Because these manifests follow CNCF open standards, they can be deployed to **AWS EKS** without altering your code:

### Step 1: Create an EKS Cluster (using `eksctl`)
```bash
eksctl create cluster \
  --name devinsight-cluster \
  --region us-east-1 \
  --nodegroup-name standard-workers \
  --node-type t3.xlarge \
  --nodes 2 \
  --nodes-min 1 \
  --nodes-max 3 \
  --managed
```

### Step 2: Configure kubectl Context for AWS
```bash
aws eks update-kubeconfig --region us-east-1 --name devinsight-cluster
```

### Step 3: Apply the DevInsight Manifests
```bash
kubectl apply -k k8s/
```

### Step 4: Expose via AWS Application Load Balancer (ALB)
In `k8s/04-apps/frontend.yaml`, change the frontend service type from `NodePort` to:
```yaml
spec:
  type: LoadBalancer
```
AWS will automatically provision an Elastic Load Balancer (ELB) with a public DNS address for your cluster.

---

## 4. Useful Operations & Troubleshooting Commands

```bash
# View logs of any service:
kubectl logs -f deployment/log-layer -n devinsight
kubectl logs -f deployment/llm-service -n devinsight
kubectl logs -f deployment/api-gateway -n devinsight

# Restart a deployment:
kubectl rollout restart deployment/log-layer -n devinsight

# Completely remove all DevInsight resources from the cluster:
kubectl delete -k k8s/
```

