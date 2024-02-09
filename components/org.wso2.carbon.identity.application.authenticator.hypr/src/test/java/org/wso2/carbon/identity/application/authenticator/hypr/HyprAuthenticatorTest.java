/*
 * Copyright (c) 2023, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.application.authenticator.hypr;

import org.apache.http.HttpStatus;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.application.authentication.framework.AuthenticatorFlowStatus;
import org.wso2.carbon.identity.application.authentication.framework.context.AuthenticationContext;
import org.wso2.carbon.identity.application.authentication.framework.exception.AuthenticationFailedException;
import org.wso2.carbon.identity.application.authentication.framework.exception.LogoutFailedException;
import org.wso2.carbon.identity.application.authenticator.hypr.common.constants.HyprAuthenticatorConstants;
import org.wso2.carbon.identity.application.authenticator.hypr.common.exception.HYPRAuthnFailedException;
import org.wso2.carbon.identity.application.authenticator.hypr.common.model.DeviceAuthenticationResponse;
import org.wso2.carbon.identity.application.authenticator.hypr.common.model.RegisteredDevice;
import org.wso2.carbon.identity.application.authenticator.hypr.common.model.RegisteredDevicesResponse;
import org.wso2.carbon.identity.application.authenticator.hypr.common.model.RequestIDResponse;
import org.wso2.carbon.identity.application.authenticator.hypr.common.model.ResponseEntity;
import org.wso2.carbon.identity.application.authenticator.hypr.common.web.HYPRAuthorizationAPIClient;
import org.wso2.carbon.identity.core.ServiceURL;
import org.wso2.carbon.identity.core.ServiceURLBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The HyprAuthenticatorTest class contains all the test cases corresponding to the HyprAuthenticator class.
 */
public class HyprAuthenticatorTest {

    private static final String apiToken = "testApiToken";
    private static final String appID = "testApp";
    private static final String baseUrl = "https://wso2.hypr.com";
    private static final String sessionDataKey = "testSessionKey";
    private static final String authType = "hypr";
    private static final String deviceId = "testDeviceID";
    private static final String protocolVersion = null;
    private static final String modelNumber = "testModelNumber";
    private static final String machineId = "testMachineID";
    private static final String username = "testUser";
    private static final String requestId = "testRequestId";
    private static final String statusCompleted = "COMPLETED";
    private static final String statusFailed = "FAILED";
    private static final String statusCanceled = "CANCELED";
    private static final String statusPending = "PENDING";
    private HyprAuthenticator hyprAuthenticator;
    @Mock
    private HyprAuthenticator mockedHyprAuthenticator;
    @Mock
    private HttpServletRequest httpServletRequest;
    @Mock
    private HttpServletResponse httpServletResponse;
    @Spy
    private AuthenticationContext context;
    @Spy
    private HyprAuthenticator spy;
    private MockedStatic<HYPRAuthorizationAPIClient> mockedHyprAuthorizationAPIClient;
    private AutoCloseable autoCloseable;
    private MockedStatic<ServiceURLBuilder> mockedServiceURLBuilder;

    @BeforeClass
    public void setUp() {

        autoCloseable = MockitoAnnotations.openMocks(this);
        hyprAuthenticator = new HyprAuthenticator();
        mockedHyprAuthorizationAPIClient = mockStatic(HYPRAuthorizationAPIClient.class);
        mockedServiceURLBuilder = mockStatic(ServiceURLBuilder.class);
    }

    @AfterClass
    public void close() throws Exception {

        mockedHyprAuthorizationAPIClient.close();
        mockedServiceURLBuilder.close();
        autoCloseable.close();
    }

    private void mockServiceURLBuilder() {

        ServiceURLBuilder builder = new ServiceURLBuilder() {

            String path = "";

            @Override
            public ServiceURLBuilder addPath(String... strings) {

                Arrays.stream(strings).forEach(x -> path += "/" + x);
                return this;
            }

            @Override
            public ServiceURLBuilder addParameter(String s, String s1) {

                return this;
            }

            @Override
            public ServiceURLBuilder setFragment(String s) {

                return this;
            }

            @Override
            public ServiceURLBuilder addFragmentParameter(String s, String s1) {

                return this;
            }

            @Override
            public ServiceURL build() {

                ServiceURL serviceURL = mock(ServiceURL.class);
                when(serviceURL.getAbsolutePublicURL()).thenReturn("https://localhost:9443" + path);
                when(serviceURL.getRelativePublicURL()).thenReturn(path);
                when(serviceURL.getRelativeInternalURL()).thenReturn(path);
                return serviceURL;
            }
        };

        when(ServiceURLBuilder.create()).thenReturn(builder);
    }

    @Test(description = "Test case for canHandle() method.")
    public void testCanHandle() {

        // The request contains the session data key and the auth type.
        when(httpServletRequest.getParameter(HyprAuthenticatorConstants.HYPR.SESSION_DATA_KEY))
                .thenReturn(sessionDataKey);
        when(httpServletRequest.getParameter(HyprAuthenticatorConstants.HYPR.AUTH_TYPE))
                .thenReturn(authType);
        Assert.assertTrue(hyprAuthenticator.canHandle(httpServletRequest));

        // The request does not contain the session data key.
        when(httpServletRequest.getParameter(HyprAuthenticatorConstants.HYPR.SESSION_DATA_KEY)).thenReturn(null);
        Assert.assertFalse(hyprAuthenticator.canHandle(httpServletRequest));

        // The request does not contain the auth type.
        when(httpServletRequest.getParameter(HyprAuthenticatorConstants.HYPR.SESSION_DATA_KEY))
                .thenReturn(sessionDataKey);
        when(httpServletRequest.getParameter(HyprAuthenticatorConstants.HYPR.AUTH_TYPE))
                .thenReturn(null);
        Assert.assertFalse(hyprAuthenticator.canHandle(httpServletRequest));
    }

    @Test(description = "Test case for getContextIdentifier() method.")
    public void testGetContextIdentifier() {

        when(httpServletRequest.getParameter("sessionDataKey")).thenReturn(sessionDataKey);
        Assert.assertEquals(hyprAuthenticator.getContextIdentifier(httpServletRequest), sessionDataKey);

        when(httpServletRequest.getParameter("sessionDataKey")).thenReturn(null);
        Assert.assertNull(hyprAuthenticator.getContextIdentifier(httpServletRequest));
    }

    @Test(description = "Test case for getFriendlyName() method.")
    public void testGetFriendlyName() {

        Assert.assertEquals(hyprAuthenticator.getFriendlyName(),
                HyprAuthenticatorConstants.HYPR.AUTHENTICATOR_FRIENDLY_NAME);
    }

    @Test(description = "Test case for getName() method.")
    public void testGetName() {

        Assert.assertEquals(hyprAuthenticator.getName(), HyprAuthenticatorConstants.HYPR.AUTHENTICATOR_NAME);
    }

    @Test(description = "Test case for process() method where the user requests to initiate the login with HYPR " +
            "without providing any username.")
    public void testProcessWithUsernameFalseAuthStatusFalse()
            throws AuthenticationFailedException, LogoutFailedException {

        doReturn(true).when(mockedHyprAuthenticator).canHandle(httpServletRequest);
        mockHttpServletRequest(null);
        doNothing().when(spy).initiateAuthenticationRequest(httpServletRequest, httpServletResponse, context);
        AuthenticatorFlowStatus status = spy.process(httpServletRequest, httpServletResponse, context);
        Assert.assertEquals(status, AuthenticatorFlowStatus.INCOMPLETE);
    }

    @Test(description = "Test case for process() method where user has successfully completed the authentication " +
            "with HYPR.")
    public void testProcessWithAuthStatusCompleted() throws AuthenticationFailedException, LogoutFailedException {

        doReturn(true).when(mockedHyprAuthenticator).canHandle(httpServletRequest);
        mockHttpServletRequest(null);
        context.setProperty(HyprAuthenticatorConstants.HYPR.AUTH_STATUS, statusCompleted);
        doNothing().when(spy).processAuthenticationResponse(httpServletRequest, httpServletResponse, context);
        AuthenticatorFlowStatus status = spy.process(httpServletRequest, httpServletResponse, context);
        Assert.assertEquals(status, AuthenticatorFlowStatus.SUCCESS_COMPLETED);
    }

    @DataProvider(name = "hyprAuthStatusProviders")
    public Object[][] getHyprAuthStatusProviders() {

        return new String[][]{
                {statusPending},
                {statusCanceled},
                {statusFailed},
        };
    }

    @Test(dataProvider = "hyprAuthStatusProviders", description = "Test case for process() method where user's " +
            "authentication flow is either pending, cancelled or failed stage")
    public void testProcessWithAuthStatusPendingOrTerminatingStatus(String retrievedAuthStatus)
            throws AuthenticationFailedException, LogoutFailedException {

        doReturn(true).when(mockedHyprAuthenticator).canHandle(httpServletRequest);
        mockHttpServletRequest(null);
        context.setProperty(HyprAuthenticatorConstants.HYPR.AUTH_STATUS, retrievedAuthStatus);

        mockServiceURLBuilder();
        AuthenticatorFlowStatus status = hyprAuthenticator.process(httpServletRequest, httpServletResponse, context);
        Assert.assertEquals(status, AuthenticatorFlowStatus.INCOMPLETE);
    }

    @Test(description = "Test case for process() method where user initiates the login request with a valid username.")
    public void testProcessWithValidUsername() throws AuthenticationFailedException, LogoutFailedException {

        doReturn(true).when(mockedHyprAuthenticator).canHandle(httpServletRequest);
        mockHttpServletRequest(username);
        mockAuthenticatorContext(baseUrl, appID, apiToken);
        mockServiceURLBuilder();

        RegisteredDevicesResponse registeredDevicesResponse = getRegisteredDevicesResponse();

        mockedHyprAuthorizationAPIClient
                .when(() -> HYPRAuthorizationAPIClient.getRegisteredDevicesRequest(baseUrl, appID, apiToken, username))
                .thenReturn(registeredDevicesResponse);

        DeviceAuthenticationResponse deviceAuthenticationResponse = getDeviceAuthenticationResponse();

        mockedHyprAuthorizationAPIClient
                .when(() -> HYPRAuthorizationAPIClient.initiateAuthenticationRequest(
                        baseUrl, appID, apiToken, username, machineId))
                .thenReturn(deviceAuthenticationResponse);

        AuthenticatorFlowStatus status = spy.process(httpServletRequest, httpServletResponse, context);
        Assert.assertEquals(status, AuthenticatorFlowStatus.INCOMPLETE);
        Assert.assertEquals(context.getProperty(HyprAuthenticatorConstants.HYPR.AUTH_STATUS),
                HyprAuthenticatorConstants.HYPR.AuthenticationStatus.PENDING.getName());
        Assert.assertEquals(context.getProperty(HyprAuthenticatorConstants.HYPR.AUTH_REQUEST_ID), requestId);
        Assert.assertEquals(context.getProperty(HyprAuthenticatorConstants.HYPR.USERNAME), username);
    }

    @Test(description = "Test case for process() method where user initiates the login request with an invalid " +
            "username.")
    public void testProcessWithInvalidUsername() throws AuthenticationFailedException, LogoutFailedException {

        doReturn(true).when(mockedHyprAuthenticator).canHandle(httpServletRequest);
        mockHttpServletRequest(username);
        mockAuthenticatorContext(baseUrl, appID, apiToken);
        mockServiceURLBuilder();

        List<RegisteredDevice> registeredDevices = new ArrayList<>();
        RegisteredDevicesResponse registeredDevicesResponse = new RegisteredDevicesResponse(registeredDevices);

        mockedHyprAuthorizationAPIClient
                .when(() -> HYPRAuthorizationAPIClient.getRegisteredDevicesRequest(baseUrl, appID, apiToken, username))
                .thenReturn(registeredDevicesResponse);

        AuthenticatorFlowStatus status = spy.process(httpServletRequest, httpServletResponse, context);
        Assert.assertEquals(status, AuthenticatorFlowStatus.INCOMPLETE);
    }

    @Test(description = "Test case for process() method which handles either invalid or expired HYPR API token " +
            "extracted from HYPR configurations upon a valid login request(i.e. a valid username)")
    public void testProcessWithValidUsernameAndInvalidApiToken()
            throws AuthenticationFailedException, LogoutFailedException {

        doReturn(true).when(mockedHyprAuthenticator).canHandle(httpServletRequest);
        mockHttpServletRequest(username);
        mockAuthenticatorContext(baseUrl, appID, apiToken);
        mockServiceURLBuilder();

        HyprAuthenticatorConstants.ErrorMessages errorMessage =
                HyprAuthenticatorConstants.ErrorMessages.HYPR_ENDPOINT_API_TOKEN_INVALID_FAILURE;

        mockedHyprAuthorizationAPIClient
                .when(() -> HYPRAuthorizationAPIClient.getRegisteredDevicesRequest(baseUrl, appID, apiToken, username))
                .thenThrow(new HYPRAuthnFailedException(errorMessage.getCode(), errorMessage.getMessage()));

        AuthenticatorFlowStatus status = spy.process(httpServletRequest, httpServletResponse, context);
        Assert.assertEquals(status, AuthenticatorFlowStatus.INCOMPLETE);
    }

    @DataProvider(name = "HYPRAuthnFailedExceptionMessageProviders")
    public Object[][] getHyprAuthnFailedExceptionMessageProviders() {

        return new HyprAuthenticatorConstants.ErrorMessages[][]{
                {HyprAuthenticatorConstants.ErrorMessages.AUTHENTICATION_FAILED_RETRIEVING_REG_DEVICES_FAILURE},
                {HyprAuthenticatorConstants.ErrorMessages.SERVER_ERROR_CREATING_HTTP_CLIENT},
                {HyprAuthenticatorConstants.ErrorMessages.AUTHENTICATION_FAILED_SENDING_PUSH_NOTIFICATION_FAILURE},
                {HyprAuthenticatorConstants.ErrorMessages.AUTHENTICATION_FAILED_RETRIEVING_HASH_ALGORITHM_FAILURE},
        };
    }

    @Test(dataProvider = "HYPRAuthnFailedExceptionMessageProviders", description = "Test case for process() method " +
            "which handles the HYPRAuthnFailedExceptions thrown from HYPRAuthorizationAPIClient upon a valid " +
            "login request(i.e. a valid username)", expectedExceptions = AuthenticationFailedException.class)
    public void testProcessWithValidUsernameAndHyprAuthnFailedException(
            HyprAuthenticatorConstants.ErrorMessages errorMessage)
            throws AuthenticationFailedException, LogoutFailedException {

        doReturn(true).when(mockedHyprAuthenticator).canHandle(httpServletRequest);
        mockHttpServletRequest(username);
        mockAuthenticatorContext(baseUrl, appID, apiToken);

        mockedHyprAuthorizationAPIClient
                .when(() -> HYPRAuthorizationAPIClient.getRegisteredDevicesRequest(baseUrl, appID, apiToken, username))
                .thenThrow(new HYPRAuthnFailedException(errorMessage.getCode(), errorMessage.getMessage()));

        spy.process(httpServletRequest, httpServletResponse, context);

    }

    @DataProvider(name = "HYPRConfigurationProviders")
    public Object[][] getHyprConfigurationProviders() {

        return new String[][]{
                {baseUrl, appID, null},
                {null, appID, apiToken},
                {baseUrl, null, apiToken},
                {baseUrl, null, null},
                {null, appID, null},
                {null, null, apiToken},
        };
    }

    @Test(dataProvider = "HYPRConfigurationProviders", description = "Test case for process() method which handles " +
            "invalid hypr configurations upon a valid login request(i.e. a valid username)",
            expectedExceptions = AuthenticationFailedException.class)
    public void testProcessWithValidUsernameAndInvalidHyprConfigurations(
            String baseUrl, String appID, String apiToken) throws AuthenticationFailedException, LogoutFailedException {

        doReturn(true).when(mockedHyprAuthenticator).canHandle(httpServletRequest);
        mockHttpServletRequest(username);
        mockAuthenticatorContext(baseUrl, appID, apiToken);
        spy.process(httpServletRequest, httpServletResponse, context);
    }

    private void mockAuthenticatorContext(String baseUrl, String appID, String apiToken) {

        Map<String, String> authenticatorProperties = new HashMap<>();
        authenticatorProperties.put(HyprAuthenticatorConstants.HYPR.BASE_URL, baseUrl);
        authenticatorProperties.put(HyprAuthenticatorConstants.HYPR.APP_ID, appID);
        authenticatorProperties.put(HyprAuthenticatorConstants.HYPR.HYPR_API_TOKEN, apiToken);
        when(context.getAuthenticatorProperties()).thenReturn(authenticatorProperties);
    }

    private void mockHttpServletRequest(String username) {

        when(httpServletRequest.getParameter(HyprAuthenticatorConstants.HYPR.SESSION_DATA_KEY))
                .thenReturn(sessionDataKey);
        when(httpServletRequest.getParameter(HyprAuthenticatorConstants.HYPR.USERNAME))
                .thenReturn(username);

        if (username != null) {
            Map<String, String[]> parameterMap = new HashMap<>();
            parameterMap.put(HyprAuthenticatorConstants.HYPR.USERNAME, new String[]{username});
            when(httpServletRequest.getParameterMap()).thenReturn(parameterMap);
        }
    }

    private DeviceAuthenticationResponse getDeviceAuthenticationResponse() {

        ResponseEntity authenticationRequestResponseEntity = new ResponseEntity();
        authenticationRequestResponseEntity.setResponseCode(HttpStatus.SC_OK);

        RequestIDResponse requestIDResponse = new RequestIDResponse();
        requestIDResponse.setRequestId(requestId);

        DeviceAuthenticationResponse deviceAuthenticationResponse = new DeviceAuthenticationResponse();
        deviceAuthenticationResponse.setStatus(authenticationRequestResponseEntity);
        deviceAuthenticationResponse.setResponse(requestIDResponse);
        return deviceAuthenticationResponse;
    }

    private RegisteredDevicesResponse getRegisteredDevicesResponse() {

        RegisteredDevice registeredDevice = new RegisteredDevice();
        registeredDevice.setDeviceId(deviceId);
        registeredDevice.setProtocolVersion(protocolVersion);
        registeredDevice.setMachineId(machineId);
        registeredDevice.setModelNumber(modelNumber);
        registeredDevice.setNamedUser(username);

        List<RegisteredDevice> registeredDevices = new ArrayList<>();
        registeredDevices.add(registeredDevice);

        return new RegisteredDevicesResponse(registeredDevices);
    }
}
