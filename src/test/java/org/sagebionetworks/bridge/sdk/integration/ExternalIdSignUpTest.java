package org.sagebionetworks.bridge.sdk.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.sdk.integration.Tests.PASSWORD;
import static org.sagebionetworks.bridge.sdk.integration.Tests.STUDY_ID_1;
import static org.sagebionetworks.bridge.util.IntegTestUtils.TEST_APP_ID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.rest.api.AuthenticationApi;
import org.sagebionetworks.bridge.rest.api.ExternalIdentifiersApi;
import org.sagebionetworks.bridge.rest.api.ForAdminsApi;
import org.sagebionetworks.bridge.rest.api.ForResearchersApi;
import org.sagebionetworks.bridge.rest.api.ParticipantsApi;
import org.sagebionetworks.bridge.rest.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.rest.model.AccountStatus;
import org.sagebionetworks.bridge.rest.model.Enrollment;
import org.sagebionetworks.bridge.rest.model.ExternalIdentifierList;
import org.sagebionetworks.bridge.rest.model.GeneratedPassword;
import org.sagebionetworks.bridge.rest.model.Role;
import org.sagebionetworks.bridge.rest.model.SignIn;
import org.sagebionetworks.bridge.rest.model.SignUp;
import org.sagebionetworks.bridge.user.TestUserHelper;
import org.sagebionetworks.bridge.user.TestUserHelper.TestUser;

public class ExternalIdSignUpTest {
    
    private String externalId1;
    private String externalId2;
    private String externalId3;
    private TestUser admin;
    private TestUser devResearcher;
    private ExternalIdentifiersApi devIdsClient;
    private ForResearchersApi researchersClient;
    private ForAdminsApi adminClient;
    private AuthenticationApi authClient;
    
    @Before
    public void before() throws Exception {
        admin = TestUserHelper.getSignedInAdmin();
        adminClient = admin.getClient(ForAdminsApi.class);
        
        devResearcher = TestUserHelper.createAndSignInUser(ExternalIdSignUpTest.class, false, Role.DEVELOPER, Role.RESEARCHER);
        externalId1 = Tests.randomIdentifier(ExternalIdSignUpTest.class);
        externalId2 = Tests.randomIdentifier(ExternalIdSignUpTest.class);
        externalId3 = Tests.randomIdentifier(ExternalIdSignUpTest.class);
        devIdsClient = devResearcher.getClient(ExternalIdentifiersApi.class);
        researchersClient = devResearcher.getClient(ForResearchersApi.class);        
        authClient = TestUserHelper.getNonAuthClient(AuthenticationApi.class, TEST_APP_ID);
    }
    
    @After
    public void deleteDeveloper() throws Exception {
        if (devResearcher != null) {
            devResearcher.signOutAndDeleteUser();
        }
    }
    
    @Test
    public void externalIdSignInTest() throws Exception {
        String userId1 = null;
        String userId2 = null;
        String userId3 = null;
        try {
            
            SignUp signUp = new SignUp();
            Enrollment en1 = new Enrollment().studyId(STUDY_ID_1).externalId(externalId1);
            signUp.setEnrollment(en1);
            signUp.setPassword(PASSWORD);
            
            // You can't enroll yourself in a study without signing a consent...we need
            // a researcher to enroll you.
            devResearcher.getClient(ParticipantsApi.class).createParticipant(signUp).execute();
            
            ExternalIdentifierList list = devIdsClient.getExternalIds(null, 5, externalId1).execute().body();
            assertEquals(1, list.getItems().size());
            assertTrue(list.getItems().get(0).isAssigned());
            
            // Prove you can sign in with this account (status = enabled)
            SignIn signIn = new SignIn().externalId(externalId1).appId(TEST_APP_ID).password(PASSWORD);
            try {
                authClient.signInV4(signIn).execute().body();
                fail("Should have thrown exception.");
            } catch(ConsentRequiredException e) {
                userId1 = e.getSession().getId();
                assertTrue(e.getSession().getExternalIds().values().contains(externalId1));
                assertEquals(AccountStatus.ENABLED, e.getSession().getStatus());
            }
            
            // Request an auto-generated password
            GeneratedPassword generatedPassword1 = researchersClient.generatePassword(
                    externalId1).execute().body();
            signIn.password(generatedPassword1.getPassword());
            try {
                authClient.signInV4(signIn).execute().body();
                fail("Should have thrown exception.");
            } catch(ConsentRequiredException e) {
                // still not consented, has succeeded
            }
            
            // Third case: create an account with an externalId, then generate the password, and the 
            // account should now be usable (enabled)
            signUp = new SignUp();
            Enrollment en3 = new Enrollment().studyId(STUDY_ID_1).externalId(externalId3);
            signUp.enrollment(en3);
            signUp.appId(TEST_APP_ID);
            devResearcher.getClient(ParticipantsApi.class).createParticipant(signUp).execute();
            
            GeneratedPassword generatedPassword3 = researchersClient.generatePassword(
                    externalId3).execute().body();
            SignIn signIn3 = new SignIn().externalId(externalId3).appId(TEST_APP_ID)
                    .password(generatedPassword3.getPassword());
            try {
                authClient.signInV4(signIn3).execute().body();
                fail("Should have thrown exception.");
            } catch(ConsentRequiredException e) {
                userId3 = e.getSession().getId();
                // still not consented, has succeeded
            }
        } finally {
            if (userId1 != null) {
                adminClient.deleteUser(userId1).execute();    
            }
            if (userId2 != null) {
                adminClient.deleteUser(userId2).execute();
            }
            if (userId3 != null) {
                adminClient.deleteUser(userId3).execute();
            }
        }
    }
}
