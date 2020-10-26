package org.sagebionetworks.bridge.sdk.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.sdk.integration.Tests.ORG_ID_1;
import static org.sagebionetworks.bridge.sdk.integration.Tests.PASSWORD;
import static org.sagebionetworks.bridge.util.IntegTestUtils.SAGE_ID;
import static org.sagebionetworks.bridge.util.IntegTestUtils.TEST_APP_ID;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.rest.api.ForAdminsApi;
import org.sagebionetworks.bridge.rest.api.OrganizationsApi;
import org.sagebionetworks.bridge.rest.api.ParticipantsApi;
import org.sagebionetworks.bridge.rest.api.StudiesApi;
import org.sagebionetworks.bridge.rest.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.rest.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.rest.model.Enrollment;
import org.sagebionetworks.bridge.rest.model.IdentifierHolder;
import org.sagebionetworks.bridge.rest.model.OrganizationList;
import org.sagebionetworks.bridge.rest.model.Role;
import org.sagebionetworks.bridge.rest.model.SignUp;
import org.sagebionetworks.bridge.rest.model.Study;
import org.sagebionetworks.bridge.rest.model.StudyList;
import org.sagebionetworks.bridge.rest.model.VersionHolder;
import org.sagebionetworks.bridge.user.TestUserHelper;
import org.sagebionetworks.bridge.user.TestUserHelper.TestUser;
import org.sagebionetworks.bridge.util.IntegTestUtils;

public class StudyTest {
    
    private List<String> studyIdsToDelete = new ArrayList<>();
    private List<String> userIdsToDelete = new ArrayList<>();
    private TestUser researcher;
    private TestUser admin;
    
    @Before
    public void before() throws Exception {
        admin = TestUserHelper.getSignedInAdmin();
        researcher = TestUserHelper.createAndSignInUser(StudyTest.class, false, Role.RESEARCHER);
    }
    
    @After
    public void after() throws Exception {
        ForAdminsApi adminsApi = admin.getClient(ForAdminsApi.class);
        for (String userId : userIdsToDelete) {
            try {
                adminsApi.deleteUser(userId).execute();
            } catch(EntityNotFoundException e) {
            }
        }
        for (String studyId : studyIdsToDelete) {
            try {
                adminsApi.deleteStudy(studyId, true).execute();    
            } catch(EntityNotFoundException e) {
            }
        }
        if (researcher != null) {
            researcher.signOutAndDeleteUser();
        }        
    }
    
//    @After
//    public void deleteResearcher() throws Exception {
//        if (testResearcher != null) {
//            testResearcher.signOutAndDeleteUser();
//        }
//    }

    @Test
    public void test() throws IOException {
        StudiesApi studiesApi = admin.getClient(StudiesApi.class);
        
        int initialCount = studiesApi.getStudies(null, null, false).execute().body().getItems().size();
        
        String id = Tests.randomIdentifier(StudyTest.class);
        Study study = new Study().identifier(id).name("Study " + id);
        
        VersionHolder holder = studiesApi.createStudy(study).execute().body();
        study.setVersion(holder.getVersion());
        studyIdsToDelete.add(id);
        
        Study retrieved = studiesApi.getStudy(id).execute().body();
        assertEquals(id, retrieved.getIdentifier());
        assertEquals("Study " + id, retrieved.getName());
        assertTrue(retrieved.getCreatedOn().isAfter(DateTime.now().minusHours(1)));
        assertTrue(retrieved.getModifiedOn().isAfter(DateTime.now().minusHours(1)));
        DateTime lastModified1 = retrieved.getModifiedOn();
        
        study.name("New test name " + id);
        VersionHolder holder2 = studiesApi.updateStudy(id, study).execute().body();
        assertNotEquals(holder.getVersion(), holder2.getVersion());
        
        Study retrieved2 = studiesApi.getStudy(id).execute().body();
        assertEquals("New test name " + id, retrieved2.getName());
        assertNotEquals(lastModified1, retrieved2.getModifiedOn());
        
        StudyList list = studiesApi.getStudies(null, null, false).execute().body();
        assertEquals(initialCount+1, list.getItems().size());
        assertFalse(list.getRequestParams().isIncludeDeleted());
        
        // logically delete it
        studiesApi.deleteStudy(id, false).execute();
        
        list = studiesApi.getStudies(null, null, false).execute().body();
        assertEquals(initialCount, list.getItems().size());
        
        list = studiesApi.getStudies(null, null, true).execute().body();
        assertTrue(list.getItems().size() > initialCount);
        assertTrue(list.getRequestParams().isIncludeDeleted());
        
        // you can still retrieve it
        Study retrieved3 = studiesApi.getStudy(id).execute().body();
        assertNotNull(retrieved3);
        
        // physically delete it
        studiesApi.deleteStudy(id, true).execute();
        
        // Now it's really gone
        list = studiesApi.getStudies(null, null, true).execute().body();
        assertEquals(initialCount, list.getItems().size());
        
        try {
            studiesApi.getStudy(id).execute();
            fail("Should have thrown an exception");
        } catch(EntityNotFoundException e) {
        }
    }
    
    @Test
    public void usersAreTaintedByStudyAssociation() throws Exception {
        // Create two studies for this test.
        String id1 = Tests.randomIdentifier(StudyTest.class);
        Study study1 = new Study().identifier(id1).name("Study " + id1);

        String id2 = Tests.randomIdentifier(StudyTest.class);
        Study study2 = new Study().identifier(id2).name("Study " + id2);
        
        StudiesApi studiesApi = admin.getClient(StudiesApi.class);
        studiesApi.createStudy(study1).execute();
        studyIdsToDelete.add(id1);
        studiesApi.createStudy(study2).execute();
        studyIdsToDelete.add(id2);
        admin.getClient(OrganizationsApi.class).addStudySponsorship(SAGE_ID, id1).execute();
        
        ParticipantsApi participantApi = researcher.getClient(ParticipantsApi.class);
        
        TestUser user = TestUserHelper.createAndSignInUser(StudyTest.class, true);
        userIdsToDelete.add(user.getUserId());
        
        // Cannot associate this user to a non-existent sub-study
        try {
            Enrollment en = new Enrollment().userId(user.getUserId()).studyId("bad-id");
            researcher.getClient(StudiesApi.class).enrollParticipant("bad-id", en).execute();
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("studyId is not a study", e.getErrors().get("studyId").get(0));
        }
        
        String email2 = IntegTestUtils.makeEmail(StudyTest.class);
        SignUp signUp2 = new SignUp().email(email2).password(PASSWORD).appId(TEST_APP_ID);
        
        // Cannot sign this user up because the studies include one the researcher does not possess.
        try {
            Enrollment en2 = new Enrollment().userId(user.getUserId()).studyId(id2);
            signUp2.enrollment(en2);
            researcher.getClient(ParticipantsApi.class).createParticipant(signUp2).execute();
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("is not a study of the caller"));
        }
        
        // User can be created if it has at least one study from the researcher creating it
        signUp2.enrollment(new Enrollment().userId(user.getUserId()).studyId(id1));
        IdentifierHolder keys = participantApi.createParticipant(signUp2).execute().body();
        userIdsToDelete.add(keys.getIdentifier());
    }
    
    @Test
    public void testSponsorship() throws Exception {
        StudiesApi adminStudiesApi = admin.getClient(StudiesApi.class);
        
        String tempStudyId = Tests.randomIdentifier(StudyTest.class);
        Study tempStudy = new Study().identifier(tempStudyId).name(tempStudyId);
        adminStudiesApi.createStudy(tempStudy).execute();
        studyIdsToDelete.add(tempStudyId);
        
        adminStudiesApi.addStudySponsor(tempStudyId, ORG_ID_1).execute();
        
        OrganizationList list = adminStudiesApi.getSponsors(tempStudyId, null, null).execute().body();
        assertTrue(list.getItems().stream().anyMatch((org) -> org.getIdentifier().equals(ORG_ID_1)));

        // The organization should see this as a sponsored study
        StudyList studyList = admin.getClient(OrganizationsApi.class).getSponsoredStudies(ORG_ID_1, null, null).execute().body();
        assertTrue(studyList.getItems().stream().anyMatch((study) -> study.getIdentifier().equals(tempStudyId)));

        adminStudiesApi.deleteStudy(tempStudyId, false).execute();
        
        // Now if we ask, we should not see this as a sponsored study
        studyList = admin.getClient(OrganizationsApi.class).getSponsoredStudies(ORG_ID_1, null, null).execute().body();
        assertFalse(studyList.getItems().stream().anyMatch((study) -> study.getIdentifier().equals(tempStudyId)));
        
        adminStudiesApi.removeStudySponsor(tempStudyId, ORG_ID_1).execute();

        list = adminStudiesApi.getSponsors(tempStudyId, null, null).execute().body();
        assertFalse(list.getItems().stream().anyMatch((org) -> org.getIdentifier().equals(ORG_ID_1)));
            
        adminStudiesApi.deleteStudy(tempStudyId, true).execute();
    }    
}
