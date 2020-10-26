package org.sagebionetworks.bridge.sdk.integration;

import static org.junit.Assert.assertEquals;
import static org.sagebionetworks.bridge.rest.model.Role.ADMIN;
import static org.sagebionetworks.bridge.rest.model.Role.DEVELOPER;
import static org.sagebionetworks.bridge.rest.model.Role.RESEARCHER;
import static org.sagebionetworks.bridge.util.IntegTestUtils.SAGE_ID;
import static org.sagebionetworks.bridge.util.IntegTestUtils.TEST_APP_ID;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.rest.api.ExternalIdentifiersApi;
import org.sagebionetworks.bridge.rest.api.ForAdminsApi;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.api.ForSuperadminsApi;
import org.sagebionetworks.bridge.rest.api.OrganizationsApi;
import org.sagebionetworks.bridge.rest.api.ParticipantsApi;
import org.sagebionetworks.bridge.rest.api.StudiesApi;
import org.sagebionetworks.bridge.rest.model.App;
import org.sagebionetworks.bridge.rest.model.Enrollment;
import org.sagebionetworks.bridge.rest.model.EnrollmentDetailList;
import org.sagebionetworks.bridge.rest.model.SignUp;
import org.sagebionetworks.bridge.rest.model.Study;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;
import org.sagebionetworks.bridge.rest.model.Withdrawal;
import org.sagebionetworks.bridge.user.TestUserHelper;
import org.sagebionetworks.bridge.user.TestUserHelper.TestUser;
import org.sagebionetworks.bridge.util.IntegTestUtils;

public class StudyMembershipTest {
    private TestUser admin;
    private TestUser appAdmin;
    private StudiesApi studiesApi;
    private Set<String> studyIdsToDelete;
    private Set<String> externalIdsToDelete;
    private Set<TestUser> usersToDelete;

    @Before
    public void before() throws Exception {
        admin = TestUserHelper.getSignedInAdmin();
        appAdmin = TestUserHelper.createAndSignInUser(StudyMembershipTest.class, false, DEVELOPER, RESEARCHER,
                ADMIN); // Sage Bionetworks
        studiesApi = admin.getClient(StudiesApi.class);

        studyIdsToDelete = new HashSet<>();
        externalIdsToDelete = new HashSet<>();
        usersToDelete = new HashSet<>();
    }

    @After
    public void after() throws Exception {
        ForSuperadminsApi superadminsApi = admin.getClient(ForSuperadminsApi.class);
        ForAdminsApi adminsApi = admin.getClient(ForAdminsApi.class);
        App app = superadminsApi.getApp(TEST_APP_ID).execute().body();
        app.setExternalIdRequiredOnSignup(false);
        superadminsApi.updateApp(app.getIdentifier(), app).execute();

        // This can only happen after external ID management is disabled.
        for (TestUser user : usersToDelete) {
            try {
                user.signOutAndDeleteUser();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
        if (appAdmin != null) {
            ExternalIdentifiersApi extIdsApi = appAdmin.getClient(ExternalIdentifiersApi.class);
            for (String externalId : externalIdsToDelete) {
                try {
                    extIdsApi.deleteExternalId(externalId).execute();    
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
            appAdmin.signOutAndDeleteUser();
        }
        for (String studyId : studyIdsToDelete) {
            try {
                adminsApi.deleteStudy(studyId, true).execute();    
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void addingExternalIdsAssociatesToStudy() throws Exception {
        App app = admin.getClient(ForAdminsApi.class).getUsersApp().execute().body();
        app.setExternalIdRequiredOnSignup(true);
        admin.getClient(ForSuperadminsApi.class).updateApp(app.getIdentifier(), app).execute();
        
        // Create two studies
        String idA = createStudy();
        String idB = createStudy();

        // Create an external ID in each study
        Enrollment extIdA = new Enrollment().studyId(idA).externalId("extA");
        Enrollment extIdB = new Enrollment().studyId(idB).externalId("extB");
        
        // create an account, sign in and consent, assigned to study A, and enroll it in study B
        TestUser user = createUser(idA, extIdA);
        extIdB.setUserId(user.getUserId());
        admin.getClient(StudiesApi.class).enrollParticipant(idB, extIdB).execute();
        
        ForConsentedUsersApi userApi = user.getClient(ForConsentedUsersApi.class);
        ParticipantsApi appAdminParticipantsApi = appAdmin.getClient(ParticipantsApi.class);

        // It has the extIdA external ID
        Map<String, String> externalIds = user.getSession().getExternalIds();
        assertEquals(extIdA.getExternalId(), externalIds.get(idA));
        assertEquals(1, externalIds.size());
        assertEquals(extIdA.getExternalId(), user.getSession().getExternalIds().get(idA));

        // participant is associated to two studies
        StudyParticipant participant = appAdminParticipantsApi.getParticipantById(
                user.getUserId(), false).execute().body();
        assertEquals(2, participant.getExternalIds().size());
        assertEquals(extIdA.getExternalId(), participant.getExternalIds().get(idA));
        assertEquals(extIdB.getExternalId(), participant.getExternalIds().get(idB));

        // Test that withdrawing blanks out the external ID relationships
        String userId = user.getUserId();
        userApi.withdrawFromApp(new Withdrawal().reason("Testing external IDs")).execute();
        
        StudyParticipant withdrawn = appAdminParticipantsApi.getParticipantById(userId, true).execute().body();
        
        assertEquals(0, withdrawn.getExternalIds().size());
        
        // One enrollment was removed through the legacy approach of set studyIds on a participant update. This actually
        // removes the enrollment, and is being phased out. The second approach called withdrawal and this preserves the 
        // second enrollment object to idB.
        EnrollmentDetailList list = appAdmin.getClient(StudiesApi.class).getEnrollees(idB, "withdrawn", true, 0, 10).execute().body();
        assertEquals(1, list.getItems().size());
        assertEquals(extIdB.getExternalId(), list.getItems().get(0).getExternalId());
    }
    
    @Test
    public void userCanAddExternalIdMembership() throws Exception {
        // Create two studies
        String idA = createStudy();
        String idB = createStudy();

        // Create an external ID in each study
        Enrollment extIdA1 = new Enrollment().studyId(idA).externalId("extA1");
        Enrollment extIdA2 = new Enrollment().studyId(idA).externalId("extA2");
        Enrollment extIdB = new Enrollment().studyId(idB).externalId("extB");

        // create an account, sign in and consent, assigned to study A
        TestUser user = TestUserHelper.createAndSignInUser(StudyMembershipTest.class, true);
        usersToDelete.add(user);
        
        ForConsentedUsersApi userApi = user.getClient(ForConsentedUsersApi.class);
        extIdA1.setUserId(user.getUserId());
        extIdA2.setUserId(user.getUserId());
        extIdB.setUserId(user.getUserId());
        admin.getClient(StudiesApi.class).enrollParticipant(extIdA1.getStudyId(), extIdA1).execute();
        admin.getClient(StudiesApi.class).enrollParticipant(extIdB.getStudyId(), extIdB).execute();

        StudyParticipant participant = admin.getClient(ParticipantsApi.class)
                .getParticipantById(user.getUserId(), false).execute().body();
        UserSessionInfo session = userApi.updateUsersParticipantRecord(participant).execute().body();
        assertEquals(extIdA1.getExternalId(), session.getExternalIds().get(idA));
        assertEquals(extIdB.getExternalId(), session.getExternalIds().get(idB));
        
        // But you cannot change an ID once set just by updating the account
        participant = admin.getClient(ParticipantsApi.class).getParticipantById(user.getUserId(), false)
                .execute().body();
        participant.setEnrollment(extIdA2);
        session = userApi.updateUsersParticipantRecord(participant).execute().body();
            
        // has not changed
        assertEquals(extIdA1.getExternalId(), session.getExternalIds().get(idA));
        assertEquals(extIdB.getExternalId(), session.getExternalIds().get(idB));
    }

    private String createStudy() throws Exception {
        String id = Tests.randomIdentifier(StudyTest.class);
        Study study = new Study().identifier(id).name("Study " + id);
        studiesApi.createStudy(study).execute();
        admin.getClient(OrganizationsApi.class).addStudySponsorship(SAGE_ID, id).execute();
        studyIdsToDelete.add(id);
        return id;
    }

    private TestUser createUser(String studyId, Enrollment enrollment) throws Exception {
        String email = IntegTestUtils.makeEmail(StudyMembershipTest.class);
        SignUp signUp = new SignUp().appId(TEST_APP_ID).email(email).password("P@ssword`1");
        signUp.enrollment(enrollment);
        TestUser user = TestUserHelper.createAndSignInUser(StudyMembershipTest.class, true, signUp);
        usersToDelete.add(user);
        return user;
    }
}