/*
 * Copyright 2016-2018. the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.appbroker.integration;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.appbroker.integration.fixtures.CloudControllerStubFixture;
import org.springframework.cloud.appbroker.integration.fixtures.OpenServiceBrokerApiFixture;
import org.springframework.cloud.servicebroker.model.instance.OperationState;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.cloud.appbroker.integration.CreateInstanceWithSpacePerServiceInstanceTargetComponentTest.APP_NAME;
import static org.springframework.cloud.appbroker.integration.CreateInstanceWithSpacePerServiceInstanceTargetComponentTest.BACKING_SERVICE_NAME;
import static org.springframework.cloud.appbroker.integration.CreateInstanceWithSpacePerServiceInstanceTargetComponentTest.BACKING_SI_NAME;

@TestPropertySource(properties = {
	"spring.cloud.appbroker.services[0].service-name=example",
	"spring.cloud.appbroker.services[0].plan-name=standard",
	"spring.cloud.appbroker.services[0].apps[0].path=classpath:demo.jar",
	"spring.cloud.appbroker.services[0].apps[0].name=" + APP_NAME,
	"spring.cloud.appbroker.services[0].apps[0].services[0].service-instance-name=" + BACKING_SI_NAME,

	"spring.cloud.appbroker.services[0].services[0].service-instance-name=" + BACKING_SI_NAME,
	"spring.cloud.appbroker.services[0].services[0].name=" + BACKING_SERVICE_NAME,
	"spring.cloud.appbroker.services[0].services[0].plan=standard",

	"spring.cloud.appbroker.services[0].target.name=SpacePerServiceInstance"
})
class CreateInstanceWithSpacePerServiceInstanceTargetComponentTest extends WiremockComponentTest {

	static final String APP_NAME = "app-new-services-target";

	static final String BACKING_SI_NAME = "my-db-service";
	static final String BACKING_SERVICE_NAME = "db-service";

	@Autowired
	private OpenServiceBrokerApiFixture brokerFixture;

	@Autowired
	private CloudControllerStubFixture cloudControllerFixture;

	@Test
	void pushAppWithServicesInSpace() {
		String serviceInstanceId = "instance-id";

		cloudControllerFixture.stubSpaceDoesNotExist(serviceInstanceId);
		cloudControllerFixture.stubCreateSpace(serviceInstanceId);
		cloudControllerFixture.stubPushAppWithHost(APP_NAME, APP_NAME + "-" + serviceInstanceId);

		// given services are available in the marketplace
		cloudControllerFixture.stubServiceExists(BACKING_SERVICE_NAME);

		// will create and bind the service instance
		cloudControllerFixture.stubCreateServiceInstance(BACKING_SI_NAME);
		cloudControllerFixture.stubCreateServiceBinding(APP_NAME, BACKING_SI_NAME);
		cloudControllerFixture.stubServiceInstanceExists(BACKING_SI_NAME);

		// when a service instance is created
		given(brokerFixture.serviceInstanceRequest())
			.when()
			.put(brokerFixture.createServiceInstanceUrl(), serviceInstanceId)
			.then()
			.statusCode(HttpStatus.ACCEPTED.value());

		// when the "last_operation" API is polled
		given(brokerFixture.serviceInstanceRequest())
			.when()
			.get(brokerFixture.getLastInstanceOperationUrl(), serviceInstanceId)
			.then()
			.statusCode(HttpStatus.OK.value())
			.body("state", is(equalTo(OperationState.IN_PROGRESS.toString())));

		String state = brokerFixture.waitForAsyncOperationComplete(serviceInstanceId);
		assertThat(state).isEqualTo(OperationState.SUCCEEDED.toString());
	}
}