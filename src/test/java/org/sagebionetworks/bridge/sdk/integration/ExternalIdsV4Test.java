package org.sagebionetworks.bridge.sdk.integration;

import static java.util.stream.Collectors.toSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.rest.model.Role.DEVELOPER;
import static org.sagebionetworks.bridge.rest.model.Role.RESEARCHER;
import static org.sagebionetworks.bridge.sdk.integration.Tests.ORG_ID_1;
import static org.sagebionetworks.bridge.sdk.integration.Tests.STUDY_ID_1;
import static org.sagebionetworks.bridge.sdk.integration.Tests.STUDY_ID_2;
import static org.sagebionetworks.bridge.util.IntegTestUtils.TEST_APP_ID;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.rest.api.AuthenticationApi;
import org.sagebionetworks.bridge.rest.api.ForAdminsApi;
import org.sagebionetworks.bridge.rest.api.ForResearchersApi;
import org.sagebionetworks.bridge.rest.api.ForSuperadminsApi;
import org.sagebionetworks.bridge.rest.api.OrganizationsApi;
import org.sagebionetworks.bridge.rest.api.StudiesApi;
import org.sagebionetworks.bridge.rest.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.rest.model.App;
import org.sagebionetworks.bridge.rest.model.Enrollment;
import org.sagebionetworks.bridge.rest.model.ExternalIdentifier;
import org.sagebionetworks.bridge.rest.model.ExternalIdentifierList;
import org.sagebionetworks.bridge.rest.model.Message;
import org.sagebionetworks.bridge.rest.model.Role;
import org.sagebionetworks.bridge.rest.model.SignIn;
import org.sagebionetworks.bridge.rest.model.SignUp;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.user.TestUserHelper;
import org.sagebionetworks.bridge.user.TestUserHelper.TestUser;

import com.google.common.collect.Lists;

import retrofit2.Response;

public class ExternalIdsV4Test {

    private String prefix;
    private TestUser admin;
    private TestUser researcher;

    @Before
    public void before() throws Exception {
        prefix = RandomStringUtils.randomAlphabetic(5);
        admin = TestUserHelper.getSignedInAdmin();
        researcher = TestUserHelper.createAndSignInUser(ExternalIdsV4Test.class, true, Role.RESEARCHER);
    }

    @After
    public void after() throws Exception {
        researcher.signOutAndDeleteUser();
    }

    @Test
    public void test() throws Exception {
        final String extIdA = prefix+Tests.randomIdentifier(ExternalIdsV4Test.class);
        final String extIdB = prefix+Tests.randomIdentifier(ExternalIdsV4Test.class);

        ForSuperadminsApi superadminClient = admin.getClient(ForSuperadminsApi.class);
        ForAdminsApi adminClient = admin.getClient(ForAdminsApi.class);
        ForResearchersApi researcherApi = researcher.getClient(ForResearchersApi.class);
        String userId = null;
        try {
            App app = superadminClient.getApp(TEST_APP_ID).execute().body();
            superadminClient.updateApp(app.getIdentifier(), app).execute();

            // Creating an external ID without a study now fails
            try {
                researcherApi.createExternalId(new ExternalIdentifier().identifier(extIdA)).execute();
                fail("Should have thrown an exception");
            } catch (InvalidEntityException e) {
            }
            // Sign up a user with an external ID specified. Just one of them: we don't have plans to
            // allow the assignment of multiple external IDs on sign up. Adding new studies is probably
            // going to happen by signing additional consents, but that's TBD.
            Enrollment enA = new Enrollment().studyId(STUDY_ID_1).externalId(extIdA);
            SignUp signUp = new SignUp().appId(TEST_APP_ID).password(Tests.PASSWORD).enrollment(enA);
            researcher.getClient(AuthenticationApi.class).signUp(signUp).execute();

            // The created account has been associated to the external ID and its related study
            StudyParticipant participant = researcherApi.getParticipantByExternalId(extIdA, false).execute().body();
            userId = participant.getId();
            assertEquals(1, participant.getExternalIds().size());
            assertEquals(extIdA, participant.getExternalIds().get(STUDY_ID_1));
            assertEquals(1, participant.getStudyIds().size());
            assertEquals(STUDY_ID_1, participant.getStudyIds().get(0));
            assertTrue(participant.getExternalIds().values().contains(extIdA));

            // Cannot create another user with this external ID. This should do nothing and fail quietly.
            Response<Message> response = researcher.getClient(AuthenticationApi.class).signUp(signUp).execute();
            assertEquals(201, response.code());

            StudyParticipant participant2 = researcherApi.getParticipantByExternalId(extIdA, false).execute().body();
            assertEquals(userId, participant2.getId());
            
            // verify filtering by assignment since we have assigned one record
            ExternalIdentifierList all = researcherApi.getExternalIds(null, 50, prefix).execute().body();

            // Assign a second external ID to an existing account. This should work, and then both IDs should 
            // be usable to retrieve the account (demonstrating that this is not simply because in the interim 
            // while migrating, we're writing the external ID to the singular externalId field).
            Enrollment enB = new Enrollment().studyId(STUDY_ID_2).externalId(extIdB).userId(userId);
            admin.getClient(StudiesApi.class).enrollParticipant(STUDY_ID_2, enB).execute();
            
            SignIn signIn = new SignIn().appId(TEST_APP_ID);
            signIn.setExternalId(extIdA);
            signIn.setPassword(Tests.PASSWORD);
            
            StudyParticipant found1 = researcherApi.getParticipantByExternalId(extIdA, false).execute().body();
            StudyParticipant found2 = researcherApi.getParticipantByExternalId(extIdB, false).execute().body();
            assertEquals(userId, found1.getId());
            assertEquals(userId, found2.getId());
        } finally {
            App app = superadminClient.getApp(TEST_APP_ID).execute().body();
            superadminClient.updateApp(app.getIdentifier(), app).execute();
            
            if (userId != null) {
                adminClient.deleteUser(userId).execute();    
            }
        }
    }

    @Test
    public void testPaging() throws Exception {
        List<ExternalIdentifier> ids = Lists.newArrayListWithCapacity(10);
        for (int i=0; i < 10; i++) {
            String studyId = (i % 2 == 0) ? STUDY_ID_1 : STUDY_ID_2;
            String identifier = (i > 5) ? ((prefix+"-foo-"+i)) : (prefix+"-"+i);
            ExternalIdentifier id = new ExternalIdentifier().identifier(identifier).studyId(studyId);
            ids.add(id);
        }
        
        ForAdminsApi adminClient = admin.getClient(ForAdminsApi.class);
        ForResearchersApi researcherApi = researcher.getClient(ForResearchersApi.class);
        TestUser user = null;
        try {
            // Create enough external IDs to page
            for (int i=0; i < 10; i++) {
                researcherApi.createExternalId(ids.get(i)).execute();    
            }
            
            Set<String> allIds = researcherApi.getExternalIds(null, 100, prefix).execute().body()
                    .getItems().stream().map(ExternalIdentifier::getIdentifier).collect(Collectors.toSet());
            
            Set<String> collectedIds = new HashSet<>();
            
            // pageSize=3, should have 4 pages 
            for (int i=0; i < 10; i += 3) {
                ExternalIdentifierList list = researcherApi.getExternalIds(i, 3, prefix).execute().body();
                for (ExternalIdentifier oneId : list.getItems()) {
                    collectedIds.add(oneId.getIdentifier());
                }
            }
            assertEquals(allIds, collectedIds);
            
            // pageSize = 10, one page with no offset key
            ExternalIdentifierList list = researcherApi.getExternalIds(null, 10, prefix).execute().body();
            assertEquals(10, list.getItems().size());
            assertEquals(Integer.valueOf(0), Integer.valueOf( list.getRequestParams().getOffsetBy()) );
            collectedIds.clear();
            for (ExternalIdentifier oneId : list.getItems()) {
                collectedIds.add(oneId.getIdentifier());
            }
            assertEquals(allIds, collectedIds);
            
            // pageSize = 30, same thing
            list = researcherApi.getExternalIds(null, 30, prefix).execute().body();
            assertEquals(10, list.getItems().size());
            assertEquals(Integer.valueOf(0), Integer.valueOf( list.getRequestParams().getOffsetBy()) );
            collectedIds.clear();
            for (ExternalIdentifier oneId : list.getItems()) {
                collectedIds.add(oneId.getIdentifier());
            }
            assertEquals(allIds, collectedIds);
            
            // Create a researcher in org 1 that sponsors only study 1, and retrieving external IDs
            // should be filtered
            SignUp signUp = new SignUp().appId(TEST_APP_ID);
            user = new TestUserHelper.Builder(ExternalIdsV4Test.class).withRoles(RESEARCHER, DEVELOPER)
                    .withConsentUser(true).withSignUp(signUp).createAndSignInUser();
            admin.getClient(OrganizationsApi.class).addMember(ORG_ID_1, user.getUserId()).execute();
            
            ForResearchersApi scopedResearcherApi = user.getClient(ForResearchersApi.class);
            ExternalIdentifierList scopedList = scopedResearcherApi.getExternalIds(null, null, null)
                    .execute().body();
            
            // Only five of them have study ID 1
            assertEquals(5, scopedList.getItems().stream()
                    .filter(id -> id.getStudyId().equals(STUDY_ID_1)).collect(Collectors.toList()).size());
            
            // You can also filter the ids and it maintains the study scoping
            scopedList = scopedResearcherApi.getExternalIds(null, null, prefix+"-foo-").execute().body();
            assertEquals(4, scopedList.getItems().stream()
                    .filter(id -> id.getStudyId() != null).collect(Collectors.toList()).size());
        } finally {
            if (user != null) {
                user.signOutAndDeleteUser();    
            }
            for (int i=0; i < 10; i++) {
                adminClient.deleteExternalId(ids.get(i).getIdentifier()).execute();    
            }
        }
    }
    
    private Set<String> getIdentifierSet(List<ExternalIdentifier> list, int offset) {
        return list.subList(offset, offset+3).stream()
                .map(ExternalIdentifier::getIdentifier).collect(toSet());
    }
}
