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
import org.sagebionetworks.bridge.rest.api.ForResearchersApi;
import org.sagebionetworks.bridge.rest.api.ParticipantsApi;
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
    private static TestUser researcher;

    @BeforeClass
    public static void before() throws Exception {
        developer = TestUserHelper.createAndSignInUser(UserParticipantTest.class, true, Role.DEVELOPER);
        researcher = TestUserHelper.createAndSignInUser(UserParticipantTest.class, true, Role.RESEARCHER);
    }

    @AfterClass
    public static void after() throws Exception {
        if (developer != null) {
            developer.signOutAndDeleteUser();    
        }
        if (researcher != null) {
            researcher.signOutAndDeleteUser();;
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
    public void cannotChangeExternalIdentifier() throws Exception {
        String externalId1 = Tests.randomIdentifier(UserParticipantTest.class);
        String externalId2 = Tests.randomIdentifier(UserParticipantTest.class);
        
        TestUser user = new TestUserHelper.Builder(UserParticipantTest.class)
                .withExternalIds(ImmutableMap.of(STUDY_ID_1, externalId1)).createAndSignInUser();
        try {
            ForConsentedUsersApi usersApi = user.getClient(ForConsentedUsersApi.class);
            StudyParticipant participant = usersApi.getUsersParticipantRecord(false).execute().body();
            assertEquals(participant.getExternalIds().get(STUDY_ID_1), externalId1);

            UserSessionInfo session = usersApi.updateUsersParticipantRecord(participant).execute().body();
            assertEquals(externalId1, session.getExternalIds().get(STUDY_ID_1));

            participant = usersApi.getUsersParticipantRecord(false).execute().body();
            assertEquals(user.getEmail(), participant.getEmail());
            assertTrue(participant.getExternalIds().values().contains(externalId1));
            
            // This doesn't do anything
            participant.setExternalIds(ImmutableMap.of(STUDY_ID_2, externalId2));
            usersApi.updateUsersParticipantRecord(participant).execute();
            
            StudyParticipant retValue = usersApi.getUsersParticipantRecord(false).execute().body();
            assertEquals(retValue.getExternalIds().get(STUDY_ID_1), externalId1);
            assertNull(retValue.getExternalIds().get(STUDY_ID_2));
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

    @Test
    public void canNotUpdateRecordNote() throws Exception {
        TestUser user = TestUserHelper.createAndSignInUser(UserParticipantTest.class, true);
        ForResearchersApi researchersApi = researcher.getClient(ForResearchersApi.class);
        try {
            ParticipantsApi participantsApi = user.getClient(ParticipantsApi.class);

            StudyParticipant preUpdateParticipant = researchersApi.getParticipantById(user.getUserId(), false)
                    .execute().body();
            assertNull(preUpdateParticipant.getFirstName());
            assertNull(preUpdateParticipant.getNote());

            StudyParticipant participant = participantsApi.getUsersParticipantRecord(false).execute().body();

            participant.setFirstName("participant attempted firstName");
            participant.setNote("participant attempted note");

            participantsApi.updateUsersParticipantRecord(participant).execute().body();

            StudyParticipant postUpdateParticipant = researchersApi.getParticipantById(user.getUserId(), false)
                    .execute().body();
            assertEquals("participant attempted firstName", postUpdateParticipant.getFirstName());
            assertNull(postUpdateParticipant.getNote());
        } finally {
            user.signOutAndDeleteUser();
        }
    }

    @Test
    public void participantCanNotEraseExistingNote() throws Exception {
        TestUser user = TestUserHelper.createAndSignInUser(UserParticipantTest.class, true);
        ForResearchersApi researchersApi = researcher.getClient(ForResearchersApi.class);
        try {
            ParticipantsApi participantsApi = user.getClient(ParticipantsApi.class);

            StudyParticipant preUpdateParticipant = researchersApi.getParticipantById(user.getUserId(), false)
                    .execute().body();
            preUpdateParticipant.setFirstName("original firstName");
            preUpdateParticipant.setNote("existing note");

            researchersApi.updateParticipant(user.getUserId(), preUpdateParticipant).execute();

            StudyParticipant participant = participantsApi.getUsersParticipantRecord(false).execute().body();
            participant.setFirstName("participant attempted firstName");
            participant.setNote("participant attempted note");

            participantsApi.updateUsersParticipantRecord(participant).execute().body();

            StudyParticipant postUpdateParticipant = researchersApi.getParticipantById(user.getUserId(), false)
                    .execute().body();
            assertEquals("participant attempted firstName", postUpdateParticipant.getFirstName());
            assertEquals("existing note", postUpdateParticipant.getNote());
        } finally {
            user.signOutAndDeleteUser();
        }
    }

    @Test
    public void canNotViewRecordNote() throws Exception {
        TestUser user = TestUserHelper.createAndSignInUser(UserParticipantTest.class, true);
        ForResearchersApi researchersApi = researcher.getClient(ForResearchersApi.class);
        try {
            ParticipantsApi participantsApi = user.getClient(ParticipantsApi.class);

            StudyParticipant preUpdateParticipant = researchersApi.getParticipantById(user.getUserId(), false)
                    .execute().body();
            preUpdateParticipant.setFirstName("participant firstName");
            preUpdateParticipant.setNote("participant note");

            researchersApi.updateParticipant(user.getUserId(), preUpdateParticipant).execute();

            StudyParticipant participant = participantsApi.getUsersParticipantRecord(false).execute().body();
            assertEquals("participant firstName", participant.getFirstName());
            assertNull(participant.getNote());

            StudyParticipant verifiedParticipant = researchersApi.getParticipantById(user.getUserId(), false)
                    .execute().body();
            assertEquals("participant firstName", verifiedParticipant.getFirstName());
            assertEquals("participant note", verifiedParticipant.getNote());
        } finally {
            user.signOutAndDeleteUser();
        }
    }

    @Test
    public void adminCanUpdateAndViewSelfNote() throws Exception {
        TestUser admin = TestUserHelper.createAndSignInUser(UserParticipantTest.class, true, Role.ADMIN);
        ParticipantsApi adminParticipantsApi = admin.getClient(ParticipantsApi.class);
        try {
            StudyParticipant originalParticipant = adminParticipantsApi.getUsersParticipantRecord(false)
                    .execute().body();
            assertNull(originalParticipant.getFirstName());
            assertNull(originalParticipant.getNote());

            originalParticipant.setFirstName("admin firstName");
            originalParticipant.setNote("admin note");
            adminParticipantsApi.updateUsersParticipantRecord(originalParticipant).execute();

            StudyParticipant updatedParticipant = adminParticipantsApi.getUsersParticipantRecord(false)
                    .execute().body();
            assertEquals("admin firstName", updatedParticipant.getFirstName());
            assertEquals("admin note", updatedParticipant.getNote());
        } finally {
            admin.signOutAndDeleteUser();
        }
    }
}
