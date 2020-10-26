package org.sagebionetworks.bridge.sdk.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.sdk.integration.Tests.STUDY_ID_1;
import static org.sagebionetworks.bridge.sdk.integration.Tests.STUDY_ID_2;
import static org.sagebionetworks.bridge.sdk.integration.Tests.assertListsEqualIgnoringOrder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.api.ParticipantsApi;
import org.sagebionetworks.bridge.rest.api.StudiesApi;
import org.sagebionetworks.bridge.rest.model.Enrollment;
import org.sagebionetworks.bridge.rest.model.Role;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;
import org.sagebionetworks.bridge.user.TestUserHelper;
import org.sagebionetworks.bridge.user.TestUserHelper.TestUser;

import java.util.List;

/**
 * Test of the participant APIs that act on the currently authenticated user, which have replaced the 
 * user profile APIs as well as individual calls to update things like dataGroups, externalId, or 
 * sharing settings.
 *
 */
@Category(IntegrationSmokeTest.class)
public class UserParticipantTest {

    private static TestUser developer;

    @BeforeClass
    public static void before() throws Exception {
        developer = TestUserHelper.createAndSignInUser(UserParticipantTest.class, true, Role.DEVELOPER);
    }

    @AfterClass
    public static void after() throws Exception {
        if (developer != null) {
            developer.signOutAndDeleteUser();    
        }
    }

    @Test
    public void canUpdateProfile() throws Exception {
        TestUser user = TestUserHelper.createAndSignInUser(UserParticipantTest.class, true);
        try {
            ParticipantsApi participantsApi = user.getClient(ParticipantsApi.class);

            StudyParticipant participant = participantsApi.getUsersParticipantRecord(false).execute().body();

            // This should be true by default, once a participant is created:
            assertTrue(participant.isNotifyByEmail());
            
            participant.setFirstName("Davey");
            participant.setLastName("Crockett");
            participant.setAttributes(new ImmutableMap.Builder<String,String>().put("can_be_recontacted","true").build());
            participant.setNotifyByEmail(null); // this should have no effect
            participantsApi.updateUsersParticipantRecord(participant).execute().body();

            participant = participantsApi.getUsersParticipantRecord(false).execute().body();
            assertEquals("Davey", participant.getFirstName());
            assertEquals("Crockett", participant.getLastName());
            assertEquals("true", participant.getAttributes().get("can_be_recontacted"));
            // This should not have been changed as the result of updating other fields
            assertTrue(participant.isNotifyByEmail());
            
            // Now update only some of the record but verify the map is still there
            participant = participantsApi.getUsersParticipantRecord(false).execute().body();
            participant.setFirstName("Davey2");
            participant.setLastName("Crockett2");
            participant.setNotifyByEmail(false);
            participantsApi.updateUsersParticipantRecord(participant).execute().body();
            
            participant = participantsApi.getUsersParticipantRecord(false).execute().body();
            assertEquals("First name updated", "Davey2", participant.getFirstName());
            assertEquals("Last name updated", "Crockett2", participant.getLastName());
            assertEquals("true", participant.getAttributes().get("can_be_recontacted"));
            assertFalse(participant.isNotifyByEmail());
        } finally {
            user.signOutAndDeleteUser();
        }
    }

    @Test
    public void canNotChangeEnrollment() throws Exception {
        String externalId1 = Tests.randomIdentifier(UserParticipantTest.class);
        String externalId2 = Tests.randomIdentifier(UserParticipantTest.class);
        
        TestUser user = TestUserHelper.createAndSignInUser(UserParticipantTest.class, false);
        try {
            TestUser admin = TestUserHelper.getSignedInAdmin();
            admin.getClient(StudiesApi.class).enrollParticipant(STUDY_ID_2, new Enrollment()
                    .externalId(externalId2).userId(user.getUserId())).execute();
            
            ForConsentedUsersApi usersApi = user.getClient(ForConsentedUsersApi.class);
            StudyParticipant participant = usersApi.getUsersParticipantRecord(false).execute().body();
            
            Enrollment en1 = new Enrollment().studyId(STUDY_ID_1).externalId(externalId1);
            participant.enrollment(en1);
            UserSessionInfo session = usersApi.updateUsersParticipantRecord(participant).execute().body();

            assertEquals(externalId2, session.getExternalIds().get(STUDY_ID_2));
            assertTrue(session.getStudyIds().contains(STUDY_ID_2));
            
            // but 1 is no longer added...use the enrollment apis for this.
            assertNull(session.getExternalIds().get(STUDY_ID_1));
            assertFalse(session.getStudyIds().contains(STUDY_ID_1));

            participant = usersApi.getUsersParticipantRecord(false).execute().body();
            assertEquals(user.getEmail(), participant.getEmail());
            assertEquals(externalId2, participant.getExternalIds().get(STUDY_ID_2));
            assertTrue(participant.getStudyIds().contains(STUDY_ID_2));
            
            assertNull(participant.getExternalIds().get(STUDY_ID_1));
            assertFalse(participant.getStudyIds().contains(STUDY_ID_1));
        } finally {
            user.signOutAndDeleteUser();
        }
    }
    
    @Test
    public void canUpdateDataGroups() throws Exception {
        List<String> dataGroups = ImmutableList.of("sdk-int-1", "sdk-int-2");

        ForConsentedUsersApi usersApi = developer.getClient(ForConsentedUsersApi.class);

        StudyParticipant participant = new StudyParticipant();
        participant.setDataGroups(dataGroups);
        usersApi.updateUsersParticipantRecord(participant).execute();

        developer.signOut();
        developer.signInAgain();
        
        participant = usersApi.getUsersParticipantRecord(false).execute().body();
        assertListsEqualIgnoringOrder(dataGroups, participant.getDataGroups());

        // now clear the values, it should be possible to remove them.
        participant.setDataGroups(ImmutableList.of());
        usersApi.updateUsersParticipantRecord(participant).execute();
        
        developer.signOut();
        developer.signInAgain();

        participant = usersApi.getUsersParticipantRecord(false).execute().body();
        assertTrue(participant.getDataGroups().isEmpty());
    }

}
