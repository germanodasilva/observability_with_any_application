# Istio Gateway with OpenShift Route
- [Istio Gateway with OpenShift Route](#istio-gateway-with-openshift-route)
  - [Setup](#setup)
  - [Istio Ingress Gateway](#istio-ingress-gateway)
    - [Canary Deployment](#canary-deployment)
- [Secure with TLS](#secure-with-tls)
  - [Service Mesh v2](#service-mesh-v2)
  - [Service Mesh v1](#service-mesh-v1)

## Setup
  ```bash
  oc new-project shareknowledge --display-name="Shareknowledge - Service Mesh"
  ```
- Deploy frontend and backend app
  ```bash
  oc create -f app.yaml -n shareknowledge
  watch -d oc get pods -n shareknowledge
  ```
- Test with cURL
  ```bash
  curl $(oc get route frontend -n shareknowledge -o jsonpath='{.spec.host}')
  ```
  Output
  ```bash
  Frontend version: v1 => [Backend: http://backend:8080, Response: 200, Body: Backend version:v1, Response:200, Host:backend-v1-6b4dd76bbc-bd4jx, Status:200, Message: Hello, Quarkus]
  ```

- Create control plane and join data plane to control plane
  - Create namespace for control plane
    ```bash
    oc new-project control-plane --display-name="Control Plane"
    ```
  - Create control plane
    ```bash
    oc create -f https://raw.githubusercontent.com/observability_with_any_application/main/resource/smcp.yaml -n control-plane
    ```
  - Wait couple of minutes for creating control plane
    ```bash
    watch -d oc get smcp -n control-plane
    ```
  - Join shareknowledge namespace into control-plane
    ```bash
    oc create -f https://raw.githubusercontent.com/germanodasilva/observability_with_any_application/main/resource/member-roll.yaml -n control-plane
    ```
  - Check Service Mesh Member Roll status
    ```bash
    oc describe smmr/default -n control-plane
    ```
- Inject sidecar to both frontend and backend
  ```bash
  oc patch deployment/frontend-v1 -p '{"spec":{"template":{"metadata":{"annotations":{"sidecar.istio.io/inject":"true"}}}}}' -n shareknowledge
  oc patch deployment/backend-v1 -p '{"spec":{"template":{"metadata":{"annotations":{"sidecar.istio.io/inject":"true"}}}}}' -n shareknowledge
  ```
- Check that sidecar is injected
  ```bash
  oc get pods -n shareknowledge
  ```
- Check network policy
  ```bash
  oc get networkpolicy/istio-expose-route-basic-install -n shareknowledge -o yaml
  ```
- Network policy istio-expose-route is automatically added to allow traffic of OpenShift's router pod to only pod with label *maistra.io/expose-route: "true"*
  ```yaml
  spec:
    ingress:
    - from:
      - namespaceSelector:
          matchLabels:
            network.openshift.io/policy-group: ingress
    podSelector:
      matchLabels:
        maistra.io/expose-route: "true"
    policyTypes:
    - Ingress
  ```
- Patch frontend with label
  ```bash
  oc patch deployment/frontend-v1 -p '{"spec":{"template":{"metadata":{"labels":{"maistra.io/expose-route":"true"}}}}}' -n shareknowledge
  ```
- Test
  ```bash
  curl $(oc get route frontend -n shareknowledge -o jsonpath='{.spec.host}')
  # Sample output
  Frontend version: v1 => [Backend: http://backend:8080, Response: 200, Body: Backend version:v1, Response:200, Host:backend-v1-5c45fb5d76-gg8sc, Status:200, Message: Hello, Quarkus]
  ```
## Istio Ingress Gateway

**Remark: You need to replace SUBDOMAIN with your cluster subdomain in every yaml files**

- Create gateway in control-plane
```bash
SUBDOMAIN=$(oc whoami --show-console  | awk -F'apps.' '{print $2}')
curl -s https://raw.githubusercontent.com/observability_with_any_application/main/resource/wildcard-gateway.yaml  | sed 's/SUBDOMAIN/'$SUBDOMAIN'/' | oc create -n control-plane -f -
```


- Create route in control-plane
```bash
SUBDOMAIN=$(oc whoami --show-console  | awk -F'apps.' '{print $2}')
curl -s https://raw.githubusercontent.com/observability_with_any_application/main/resource/frontend-route-istio.yaml | sed 's/SUBDOMAIN/'$SUBDOMAIN'/' | oc create -n control-plane -f -
```
- Crate virtual service in shareknowledge
```bash
SUBDOMAIN=$(oc whoami --show-console  | awk -F'apps.' '{print $2}')
curl -s https://raw.githubusercontent.com/observability_with_any_application/main/resource/frontend-virtual-service.yaml | sed 's/SUBDOMAIN/'$SUBDOMAIN'/' | oc create -n shareknowledge -f -
```
- Test gateway
```bash
curl $(oc get route/frontend -n control-plane -o jsonpath='{.spec.host}')
```
### Canary Deployment
Deploy frontend v2 and configure canary deployment to route only request from Firefox to v2
- Deploy frontend-v2
```bash
oc create -f https://raw.githubusercontent.com/observability_with_any_application/main/resource/frontend-v2-deployment.yaml -n shareknowledge
```
- Create [destination rule](frontend-destination-rule.yaml) with subgroup based on version
  ```yaml
  subsets:
  - name: v1
    labels:
      app: frontend
      version: v1
    trafficPolicy:
      loadBalancer:
        simple: ROUND_ROBIN
  - name: v2
    labels:
      app: frontend
      version: v2
    trafficPolicy:
      loadBalancer:
        simple: ROUND_ROBIN
  ```
  Create destination rule
  ```bash
  oc apply -f https://raw.githubusercontent.com/observability_with_any_application/main/resource/frontend-destination-rule.yaml -n shareknowledge
  ```
- Update [frontend virtual service](frontend-virtual-service-canary.yaml) with to routing by header
  ```yaml
    http:
  - match:
    - headers:
        user-agent:
          regex: (.*)Firefox(.*)
    route:
    - destination:
        host: frontend
        port:
          number: 8080
        subset: v2
  - route:
    - destination:
        host: frontend
        port:
          number: 8080
        subset: v1
  ```
  Create virtual service
  ```bash
  SUBDOMAIN=$(oc whoami --show-console  | awk -F'apps.' '{print $2}')
  curl -s https://raw.githubusercontent.com/observability_with_any_application/main/resource/frontend-virtual-service-canary.yaml |sed 's/SUBDOMAIN/'$SUBDOMAIN'/'|oc apply -n shareknowledge -f -
  ```
- Test
  - Set User-Agent to Firefox
    ```bash
    #Test with header User-Agent contains "Firefox"
    curl -H "User-Agent:Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:78.0) Gecko/20100101 Firefox/78.0" $(oc get route frontend -n control-plane -o jsonpath='{.spec.host}')
    ```
    Output
    ```
    Frontend version: v2 => [Backend: http://backend:8080, Response: 200, Body: Backend version:v1, Response:200, Host:backend-v1-5c45fb5d76-gg8sc, Status:200, Message: Hello, Quarkus]
    ```
  - Set User-Agent to Chrome
    ```bash
    curl -H "User-Agent:Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/79.0.3945.130 Safari/537.36 Edg/79.0.309.71" $(oc get route frontend -n control-plane -o jsonpath='{.spec.host}')
    ```
    Output
    ```
    Frontend version: v1 => [Backend: http://backend:8080, Response: 200, Body: Backend version:v1, Response:200, Host:backend-v1-5c45fb5d76-gg8sc, Status:200, Message: Hello, Quarkus]
    ```
# Secure with TLS
## Service Mesh v2
- Create cretificate and secret key using [create-certificate.sh](create-certificate.sh)
  ```bash
  curl -s https://raw.githubusercontent.com/observability_with_any_application/main/resource/create-certificate.sh | bash /dev/stdin
  ```
- Create secrets
  ```bash
  oc create secret generic frontend-credential \
  --from-file=tls.key=certs/frontend.key \
  --from-file=tls.crt=certs/frontend.crt \
  -n control-plane
  ```
- Update [gateway](wildcard-gateway-tls.yaml) with simple mode TLS
  ```bash
  SUBDOMAIN=$(oc whoami --show-console  | awk -F'apps.' '{print $2}')
  curl -s https://raw.githubusercontent.com/observability_with_any_application/main/resource/wildcard-gateway-tls.yaml|sed 's/SUBDOMAIN/'$SUBDOMAIN'/' | oc apply -n control-plane -f -
  ```
- Update [frontend router](frontend-route-istio-passthrough.yaml) to passthrough mode
  ```bash
  SUBDOMAIN=$(oc whoami --show-console  | awk -F'apps.' '{print $2}')
  curl -s https://raw.githubusercontent.com/observability_with_any_application/main/resource/frontend-route-istio-passthrough.yaml |sed 's/SUBDOMAIN/'$SUBDOMAIN'/' | oc apply -n control-plane -f -
  ```
- Test
  ```bash
  curl -vk https://$(oc get route frontend -n control-plane -o jsonpath='{.spec.host}')
  ```
## Service Mesh v1
- Example of [Service Mesh Control Plane](smcp-v1-ha.yaml)
- Create secret
```bash
oc create secret tls istio-ingressgateway-certs  \
--cert certs/tls.crt --key certs/tls.key -n control-plane
```
- Restart ingress gateway
```bash
oc patch deployment istio-ingressgateway  \
-p '{"spec":{"template":{"metadata":{"annotations":{"kubectl.kubernetes.io/restartedAt": "'`date +%FT%T%z`'"}}}}}' -n control-plane
```
- Update [gateway](wildcard-gateway-tls.yaml) with simple mode TLS
  ```bash
  SUBDOMAIN=$(oc whoami --show-console  | awk -F'apps.' '{print $2}')
  curl -s https://raw.githubusercontent.com/observability_with_any_application/main/resource/wildcard-gateway-tls-v1.yaml | sed 's/SUBDOMAIN/'$SUBDOMAIN'/' | oc apply -n control-plane -f -
  ```
- Update [frontend router](frontend-route-istio-passthrough.yaml) to passthrough mode
  ```bash
  SUBDOMAIN=$(oc whoami --show-console  | awk -F'apps.' '{print $2}')
  curl -s https://raw.githubusercontent.com/observability_with_any_application/main/resource/frontend-route-istio-passthrough.yaml | sed 's/SUBDOMAIN/'$SUBDOMAIN'/' | oc apply -n control-plane -f -
  ```
- Test
  ```bash
  curl -vk https://$(oc get route frontend -n control-plane -o jsonpath='{.spec.host}')
  ```
- Verify certificate in ingress gateway pod
  ```bash
  oc exec $(oc get pods --no-headers -l istio=ingressgateway | head -n 1 | awk '{print $1}') -- cat /etc/istio/ingressgateway-certs/tls.crt
  ```
  