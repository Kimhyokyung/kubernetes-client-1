/**
 * Copyright (C) 2015 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.kubernetes.client.examples;

import com.ning.http.client.ws.WebSocket;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FullExample {

  private static final Logger logger = LoggerFactory.getLogger(FullExample.class);

  public static void main(String[] args) throws InterruptedException {
    String master = "https://localhost:8443/";
    if (args.length == 1) {
      master = args[0];
    }

    DefaultKubernetesClient.Config config = new DefaultKubernetesClient.ConfigBuilder().masterUrl(master).build();
    try (final KubernetesClient client = new DefaultKubernetesClient(config)) {
      try {
        // Create a namespace for all our stuff
        Namespace ns = new NamespaceBuilder().withNewMetadata().withName("thisisatest").addToLabels("this", "rocks").endMetadata().build();
        log("Created namespace", client.namespaces().create(ns));

        // Get the namespace by name
        log("Get namespace by name", client.namespaces().withName("thisisatest").get());
        // Get the namespace by label
        log("Get namespace by label", client.namespaces().withLabel("this", "rocks").list());

        ResourceQuota quota = new ResourceQuotaBuilder().withNewMetadata().withName("pod-quota").endMetadata().withNewSpec().addToHard("pods", new Quantity("4")).endSpec().build();
        log("Create resource quota", client.resourceQuotas().inNamespace("thisisatest").create(quota));

        //Watch the RCs in the namespace in a separate thread
        Thread watcherThread = new Thread(new Runnable() {
          @Override
          public void run() {
            try (WebSocket ws = client.replicationControllers().inNamespace("thisisatest").watch(new Watcher<ReplicationController>() {
                @Override
                public void eventReceived(Action action, ReplicationController resource) {
                  logger.info("{}: {}", action, resource);
                }
              })) {
              Thread.sleep(60000);
            } catch (KubernetesClientException e) {
              logger.error("Could not watch resources", e);
            } catch (InterruptedException e) {
              //
            }
          }
        });
        watcherThread.setDaemon(true);
        watcherThread.start();

        // Create an RC
        ReplicationController rc = new ReplicationControllerBuilder()
          .withNewMetadata().withName("nginx-controller").addToLabels("server", "nginx").endMetadata()
          .withNewSpec().withReplicas(0)
          .withNewTemplate()
          .withNewMetadata().addToLabels("server", "nginx").endMetadata()
          .withNewSpec()
          .addNewContainer().withName("nginx").withImage("nginx")
          .addNewPort().withContainerPort(80).endPort()
          .endContainer()
          .endSpec()
          .endTemplate()
          .endSpec().build();

        log("Created RC", client.replicationControllers().inNamespace("thisisatest").create(rc));

        log("Created RC with inline DSL",
          client.replicationControllers().inNamespace("thisisatest").createNew()
            .withNewMetadata().withName("nginx2-controller").addToLabels("server", "nginx").endMetadata()
            .withNewSpec().withReplicas(0)
            .withNewTemplate()
            .withNewMetadata().addToLabels("server", "nginx2").endMetadata()
            .withNewSpec()
            .addNewContainer().withName("nginx").withImage("nginx")
            .addNewPort().withContainerPort(80).endPort()
            .endContainer()
            .endSpec()
            .endTemplate()
            .endSpec().done());

        // Get the RC by name in namespace
        log("Get RC by name in namespace", client.replicationControllers().inNamespace("thisisatest").withName("nginx-controller").get());
        // Get the RC by label
        log("Get RC by label", client.replicationControllers().withLabel("server", "nginx").list());
        // Get the RC without label
        log("Get RC without label", client.replicationControllers().withoutLabel("server", "apache").list());
        // Get the RC with label in
        log("Get RC with label in", client.replicationControllers().withLabelIn("server", "nginx").list());
        // Get the RC with label in
        log("Get RC with label not in", client.replicationControllers().withLabelNotIn("server", "apache").list());
        // Get the RC by label in namespace
        log("Get RC by label in namespace", client.replicationControllers().inNamespace("thisisatest").withLabel("server", "nginx").list());
        // Update the RC
        client.replicationControllers().inNamespace("thisisatest").withName("nginx-controller").edit().editMetadata().addToLabels("new", "label").endMetadata().done();

        Thread.sleep(1000);

        //Update the RC inline
        client.replicationControllers().inNamespace("thisisatest").withName("nginx-controller").edit()
          .editMetadata()
            .addToLabels("another", "label")
          .endMetadata()
          .done();

        log("Updated RC");
        // Clean up the RC
        client.replicationControllers().inNamespace("thisisatest").withName("nginx-controller").delete();
        client.replicationControllers().inNamespace("thisisatest").withName("nginx2-controller").delete();
        log("Deleted RCs");

        //Create an ohter RC inline
        client.replicationControllers().inNamespace("thisisatest").createNew().withNewMetadata().withName("nginx-controller").addToLabels("server", "nginx").endMetadata()
          .withNewSpec().withReplicas(0)
          .withNewTemplate()
          .withNewMetadata().addToLabels("server", "nginx").endMetadata()
          .withNewSpec()
          .addNewContainer().withName("nginx").withImage("nginx")
          .addNewPort().withContainerPort(80).endPort()
          .endContainer()
          .endSpec()
          .endTemplate()
          .endSpec().done();
         log("Created inline RC");

        client.replicationControllers().inNamespace("thisisatest").withName("nginx-controller").delete();
        log("Deleted RC");

        log("Created RC", client.replicationControllers().inNamespace("thisisatest").create(rc));
        client.replicationControllers().withLabel("server", "nginx").delete();
        log("Deleted RC by label");

        log("Created RC", client.replicationControllers().inNamespace("thisisatest").create(rc));
        client.replicationControllers().inNamespace("thisisatest").withField("metadata.name", "nginx-controller").delete();
        log("Deleted RC by field");

      } finally {
        // And finally clean up the namespace
        client.namespaces().withName("thisisatest").delete();
        log("Deleted namespace");
      }
    } catch (KubernetesClientException e) {
      logger.error(e.getMessage(), e);

      Throwable[] suppressed = e.getSuppressed();
      if (suppressed != null) {
        for (Throwable t : suppressed) {
          logger.error(t.getMessage(), t);
        }
      }
    }
  }

  private static void log(String action, Object obj) {
    logger.info("{}: {}", action, obj);
  }

  private static void log(String action) {
    logger.info(action);
  }
}