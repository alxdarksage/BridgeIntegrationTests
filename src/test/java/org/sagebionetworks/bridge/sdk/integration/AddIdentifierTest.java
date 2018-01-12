package org.sagebionetworks.bridge.sdk.integration;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.api.ParticipantsApi;
import org.sagebionetworks.bridge.rest.api.UsersApi;
import org.sagebionetworks.bridge.rest.model.IdentifierHolder;
import org.sagebionetworks.bridge.rest.model.Phone;
import org.sagebionetworks.bridge.rest.model.Role;
import org.sagebionetworks.bridge.rest.model.SignUp;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;
import org.sagebionetworks.bridge.sdk.integration.TestUserHelper.TestUser;

/**
 * Tests the ability to add email/phone to an account that has only one identifier, and that neither of 
 * these can be changed on update once they are added.
 */
public class AddIdentifierTest {
    
    private static final Phone OTHER_PHONE = new Phone().number("4082585869").regionCode("US");
    
    private TestUser admin;
    private TestUser user;
    private TestUser researcher;
    private IdentifierHolder holder;
    
    @Before
    public void before() throws Exception {
        admin = TestUserHelper.getSignedInAdmin();
    }
    
    @After
    public void after() throws Exception {
        if (user != null) {
            user.signOutAndDeleteUser();
        }
        if (holder != null) {
            admin.getClient(UsersApi.class).deleteUser(holder.getIdentifier()).execute();
        }
        if (researcher != null) {
            researcher.signOutAndDeleteUser();
        }
    }
    
    private SignUp createSignUp(String email, Phone phone) {
        return new SignUp()
                .study(Tests.STUDY_ID)
                .email(email)
                .phone(phone)
                .password(Tests.PASSWORD);
    }
    
    @Test
    public void participantPhoneCanBeAdded() throws Exception {
        String emailAddress = Tests.makeEmail(AddIdentifierTest.class);
        SignUp signUp = createSignUp(emailAddress, null);
        user = new TestUserHelper.Builder(AddIdentifierTest.class).withConsentUser(true).withSignUp(signUp).createUser();
        user.signInAgain();
        
        ForConsentedUsersApi userApi = user.getClient(ForConsentedUsersApi.class);
        StudyParticipant participant = userApi.getUsersParticipantRecord().execute().body();
        
        participant.phone(Tests.PHONE);
        
        UserSessionInfo info = userApi.updateUsersParticipantRecord(participant).execute().body();
        assertEquals(Tests.PHONE.getNumber(), info.getPhone().getNumber());
        
        StudyParticipant updatedParticipant = userApi.getUsersParticipantRecord().execute().body();
        assertEquals(Tests.PHONE.getNumber(), updatedParticipant.getPhone().getNumber());
    }
    
    @Test
    public void participantPhoneCannotBeChanged() throws Exception {
        String emailAddress = Tests.makeEmail(AddIdentifierTest.class);
        SignUp signUp = createSignUp(emailAddress, Tests.PHONE);
        user = new TestUserHelper.Builder(AddIdentifierTest.class).withConsentUser(true).withSignUp(signUp).createUser();
        user.signInAgain();
        
        ForConsentedUsersApi userApi = user.getClient(ForConsentedUsersApi.class);
        StudyParticipant participant = userApi.getUsersParticipantRecord().execute().body();
        assertEquals(Tests.PHONE.getNumber(), participant.getPhone().getNumber());
        
        participant.phone(OTHER_PHONE);
        
        // These have not been changed to other phone
        UserSessionInfo info = userApi.updateUsersParticipantRecord(participant).execute().body();
        assertEquals(Tests.PHONE.getNumber(), info.getPhone().getNumber());
        
        StudyParticipant updatedParticipant = userApi.getUsersParticipantRecord().execute().body();
        assertEquals(Tests.PHONE.getNumber(), updatedParticipant.getPhone().getNumber());
    }
    
    @Test
    public void participantEmailCannotBeChangedWhenPhoneAdded() throws Exception {
        String emailAddress = Tests.makeEmail(AddIdentifierTest.class);
        SignUp signUp = createSignUp(emailAddress, null);
        user = new TestUserHelper.Builder(AddIdentifierTest.class).withConsentUser(true).withSignUp(signUp).createUser();
        user.signInAgain();
        
        ForConsentedUsersApi userApi = user.getClient(ForConsentedUsersApi.class);
        StudyParticipant participant = userApi.getUsersParticipantRecord().execute().body();
        
        participant.email("another@email.com");
        participant.phone(Tests.PHONE);
        
        // Email has not been changed, but phone has been added
        UserSessionInfo info = userApi.updateUsersParticipantRecord(participant).execute().body();
        assertEquals(emailAddress, info.getEmail());
        assertEquals(Tests.PHONE.getNumber(), info.getPhone().getNumber());
        
        StudyParticipant updatedParticipant = userApi.getUsersParticipantRecord().execute().body();
        assertEquals(emailAddress, updatedParticipant.getEmail());
        assertEquals(Tests.PHONE.getNumber(), updatedParticipant.getPhone().getNumber());
    }

    @Test
    public void participantEmailCanBeAdded() throws Exception {
        String emailAddress = Tests.makeEmail(AddIdentifierTest.class);
        SignUp signUp = createSignUp(null, Tests.PHONE);
        user = new TestUserHelper.Builder(AddIdentifierTest.class).withConsentUser(true).withSignUp(signUp).createUser();
        user.signInAgain();
        
        ForConsentedUsersApi userApi = user.getClient(ForConsentedUsersApi.class);
        StudyParticipant participant = userApi.getUsersParticipantRecord().execute().body();
        
        participant.email(emailAddress);
        
        // Email address has been added
        UserSessionInfo info = userApi.updateUsersParticipantRecord(participant).execute().body();
        assertEquals(emailAddress, info.getEmail());
        
        StudyParticipant updatedParticipant = userApi.getUsersParticipantRecord().execute().body();
        assertEquals(emailAddress, updatedParticipant.getEmail());
    }
    
    @Test
    public void participantEmailCannotBeChanged() throws Exception {
        String emailAddress = Tests.makeEmail(AddIdentifierTest.class);
        SignUp signUp = createSignUp(emailAddress, null);
        user = new TestUserHelper.Builder(AddIdentifierTest.class).withConsentUser(true).withSignUp(signUp).createUser();
        user.signInAgain();
        
        ForConsentedUsersApi userApi = user.getClient(ForConsentedUsersApi.class);
        StudyParticipant participant = userApi.getUsersParticipantRecord().execute().body();
        
        participant.email("another@email.com");
        
        // These have not been changed to other phone
        UserSessionInfo info = userApi.updateUsersParticipantRecord(participant).execute().body();
        assertEquals(emailAddress, info.getEmail());
        
        StudyParticipant updatedParticipant = userApi.getUsersParticipantRecord().execute().body();
        assertEquals(emailAddress, updatedParticipant.getEmail());        
    }
    
    @Test
    public void participantPhoneCannotBeChangedWhenEmailAdded() throws Exception {
        String emailAddress = Tests.makeEmail(AddIdentifierTest.class);
        SignUp signUp = createSignUp(null, Tests.PHONE);
        user = new TestUserHelper.Builder(AddIdentifierTest.class).withConsentUser(true).withSignUp(signUp).createUser();
        user.signInAgain();
        
        ForConsentedUsersApi userApi = user.getClient(ForConsentedUsersApi.class);
        StudyParticipant participant = userApi.getUsersParticipantRecord().execute().body();
        
        participant.phone(OTHER_PHONE);
        participant.email(emailAddress);
        
        // Email address has been added
        UserSessionInfo info = userApi.updateUsersParticipantRecord(participant).execute().body();
        assertEquals(emailAddress, info.getEmail());
        assertEquals(Tests.PHONE.getNumber(), info.getPhone().getNumber());
        
        StudyParticipant updatedParticipant = userApi.getUsersParticipantRecord().execute().body();
        assertEquals(emailAddress, updatedParticipant.getEmail());
        assertEquals(Tests.PHONE.getNumber(), updatedParticipant.getPhone().getNumber());
    }
    
    @Test
    public void phoneCanBeAdded() throws Exception {
        researcher = TestUserHelper.createAndSignInUser(AddIdentifierTest.class, false, Role.RESEARCHER);
        
        String emailAddress = Tests.makeEmail(AddIdentifierTest.class);
        SignUp signUp = createSignUp(emailAddress, null);
        
        ParticipantsApi participantsApi = researcher.getClient(ParticipantsApi.class);
        
        holder = participantsApi.createParticipant(signUp).execute().body();
        StudyParticipant participant = participantsApi.getParticipant(holder.getIdentifier()).execute().body();
        participant.phone(Tests.PHONE);
        
        participantsApi.updateParticipant(participant.getId(), participant).execute();
        
        StudyParticipant updatedParticipant = participantsApi.getParticipant(holder.getIdentifier()).execute().body();
        assertEquals(Tests.PHONE.getNumber(), updatedParticipant.getPhone().getNumber());
    }
    
    @Test
    public void phoneCannotBeChanged() throws Exception {
        researcher = TestUserHelper.createAndSignInUser(AddIdentifierTest.class, false, Role.RESEARCHER);
        
        SignUp signUp = createSignUp(null, Tests.PHONE);
        ParticipantsApi participantsApi = researcher.getClient(ParticipantsApi.class);
        holder = participantsApi.createParticipant(signUp).execute().body();
        
        StudyParticipant participant = participantsApi.getParticipant(holder.getIdentifier()).execute().body();
        participant.phone(OTHER_PHONE);
        participantsApi.updateParticipant(participant.getId(), participant).execute();
        
        StudyParticipant updatedParticipant = participantsApi.getParticipant(holder.getIdentifier()).execute().body();
        assertEquals(Tests.PHONE.getNumber(), updatedParticipant.getPhone().getNumber());
    }
    
    @Test
    public void emailCannotBeChangedWhenPhoneAdded() throws Exception {
        researcher = TestUserHelper.createAndSignInUser(AddIdentifierTest.class, false, Role.RESEARCHER);

        String emailAddress = Tests.makeEmail(AddIdentifierTest.class);
        SignUp signUp = createSignUp(emailAddress, null);
        ParticipantsApi participantsApi = researcher.getClient(ParticipantsApi.class);
        holder = participantsApi.createParticipant(signUp).execute().body();
        
        StudyParticipant participant = participantsApi.getParticipant(holder.getIdentifier()).execute().body();
        participant.email("another@email.com");
        participant.phone(Tests.PHONE);
        participantsApi.updateParticipant(holder.getIdentifier(), participant).execute();
        
        // Email has not been changed, but phone has been added
        StudyParticipant updatedParticipant = participantsApi.getParticipant(holder.getIdentifier()).execute().body();
        assertEquals(emailAddress, updatedParticipant.getEmail());
        assertEquals(Tests.PHONE.getNumber(), updatedParticipant.getPhone().getNumber());
    }

    @Test
    public void emailCanBeAdded() throws Exception {
        researcher = TestUserHelper.createAndSignInUser(AddIdentifierTest.class, false, Role.RESEARCHER);

        String emailAddress = Tests.makeEmail(AddIdentifierTest.class);
        SignUp signUp = createSignUp(null, Tests.PHONE);
        ParticipantsApi participantsApi = researcher.getClient(ParticipantsApi.class);
        holder = participantsApi.createParticipant(signUp).execute().body();
        
        StudyParticipant participant = participantsApi.getParticipant(holder.getIdentifier()).execute().body();
        participant.email(emailAddress);
        participantsApi.updateParticipant(holder.getIdentifier(), participant).execute();
        
        // Email address has been added
        StudyParticipant updatedParticipant = participantsApi.getParticipant(holder.getIdentifier()).execute().body();
        assertEquals(emailAddress, updatedParticipant.getEmail());
        assertEquals(Tests.PHONE.getNumber(), updatedParticipant.getPhone().getNumber());
    }
    
    @Test
    public void emailCannotBeChanged() throws Exception {
        researcher = TestUserHelper.createAndSignInUser(AddIdentifierTest.class, false, Role.RESEARCHER);

        String emailAddress = Tests.makeEmail(AddIdentifierTest.class);
        SignUp signUp = createSignUp(emailAddress, null);
        ParticipantsApi participantsApi = researcher.getClient(ParticipantsApi.class);
        holder = participantsApi.createParticipant(signUp).execute().body();
        
        StudyParticipant participant = participantsApi.getParticipant(holder.getIdentifier()).execute().body();
        participant.email("updated@email.com");
        participantsApi.updateParticipant(holder.getIdentifier(), participant).execute();
        
        // Email address has been added
        StudyParticipant updatedParticipant = participantsApi.getParticipant(holder.getIdentifier()).execute().body();
        assertEquals(emailAddress, updatedParticipant.getEmail());
    }
    
    @Test
    public void phoneCannotBeChangedWhenEmailAdded() throws Exception {
        researcher = TestUserHelper.createAndSignInUser(AddIdentifierTest.class, false, Role.RESEARCHER);

        String emailAddress = Tests.makeEmail(AddIdentifierTest.class);
        SignUp signUp = createSignUp(null, Tests.PHONE);
        ParticipantsApi participantsApi = researcher.getClient(ParticipantsApi.class);
        holder = participantsApi.createParticipant(signUp).execute().body();
        
        StudyParticipant participant = participantsApi.getParticipant(holder.getIdentifier()).execute().body();
        participant.phone(OTHER_PHONE);
        participant.email(emailAddress);
        participantsApi.updateParticipant(holder.getIdentifier(), participant).execute();
        
        // Email address has been added
        StudyParticipant updatedParticipant = participantsApi.getParticipant(holder.getIdentifier()).execute().body();
        assertEquals(emailAddress, updatedParticipant.getEmail());
        assertEquals(Tests.PHONE.getNumber(), updatedParticipant.getPhone().getNumber());
    }
    
    @Test
    public void cannotBreakSystemByChangingPhoneAndEmail() throws Exception {
        researcher = TestUserHelper.createAndSignInUser(AddIdentifierTest.class, false, Role.RESEARCHER);

        String emailAddress = Tests.makeEmail(AddIdentifierTest.class);
        SignUp signUp = createSignUp(emailAddress,  Tests.PHONE);
        ParticipantsApi participantsApi = researcher.getClient(ParticipantsApi.class);
        holder = participantsApi.createParticipant(signUp).execute().body();
        
        StudyParticipant participant = participantsApi.getParticipant(holder.getIdentifier()).execute().body();
        participant.phone(OTHER_PHONE);
        participant.email("email@email.com");
        participantsApi.updateParticipant(holder.getIdentifier(), participant).execute();
        
        // Email address has been added
        StudyParticipant updatedParticipant = participantsApi.getParticipant(holder.getIdentifier()).execute().body();
        assertEquals(emailAddress, updatedParticipant.getEmail());
        assertEquals(Tests.PHONE.getNumber(), updatedParticipant.getPhone().getNumber());
    }
}
