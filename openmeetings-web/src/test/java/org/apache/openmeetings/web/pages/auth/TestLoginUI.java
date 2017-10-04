/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") +  you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.openmeetings.web.pages.auth;

import static org.apache.openmeetings.util.OpenmeetingsVariables.CONFIG_EMAIL_AT_REGISTER;
import static org.apache.openmeetings.util.OpenmeetingsVariables.CONFIG_EMAIL_VERIFICATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import org.apache.openmeetings.AbstractWicketTester;
import org.apache.openmeetings.db.entity.basic.Configuration;
import org.apache.openmeetings.db.entity.user.User;
import org.apache.openmeetings.web.app.Application;
import org.apache.openmeetings.web.app.WebSession;
import org.apache.openmeetings.web.pages.ActivatePage;
import org.apache.openmeetings.web.pages.MainPage;
import org.apache.openmeetings.web.pages.ResetPage;
import org.apache.wicket.extensions.markup.html.captcha.CaptchaImageResource;
import org.apache.wicket.feedback.ExactLevelFeedbackMessageFilter;
import org.apache.wicket.feedback.FeedbackMessage;
import org.apache.wicket.markup.html.image.Image;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.resource.IResource.Attributes;
import org.apache.wicket.util.tester.FormTester;
import org.junit.Test;

import com.googlecode.wicket.jquery.ui.widget.dialog.ButtonAjaxBehavior;

public class TestLoginUI extends AbstractWicketTester {
	private final static String PATH_REGISTER = "register:form";

	private void checkLogin(String login, String pass) {
		FormTester formTester = tester.newFormTester("signin:signin");
		formTester.setValue("login", login);
		formTester.setValue("pass", pass);
		formTester.submit("submit");

		tester.assertNoErrorMessage();
		tester.assertRenderedPage(MainPage.class);
		WebSession ws = (WebSession)tester.getSession();
		assertTrue("Login should be successful", ws.isSignedIn());
	}

	@Test
	public void testValidLogin() {
		tester.startPage(MainPage.class);
		tester.assertRenderedPage(SignInPage.class);

		checkLogin(adminUsername, userpass);
	}

	@Test
	public void testEmptyLogin() {
		tester.startPage(SignInPage.class);
		tester.assertRenderedPage(SignInPage.class);

		FormTester formTester = tester.newFormTester("signin:signin");
		formTester.submit("submit");

		assertEquals("There should be exactly 2 errors", 2,
				tester.getFeedbackMessages(new ExactLevelFeedbackMessageFilter(FeedbackMessage.ERROR)).size());
	}

	private FormTester showRegister() {
		tester.startPage(SignInPage.class);
		tester.assertRenderedPage(SignInPage.class);

		ButtonAjaxBehavior b = getButtonBehavior("signin", "register");
		tester.executeBehavior(b);
		return tester.newFormTester(PATH_REGISTER);
	}

	@Test
	public void testEmptyRegister() {
		FormTester formTester = showRegister();
		formTester.submit("submit");
		assertEquals("There should be exactly 8 errors", 8,
				tester.getFeedbackMessages(new ExactLevelFeedbackMessageFilter(FeedbackMessage.ERROR)).size());
	}

	private FormTester showForget() {
		tester.startPage(SignInPage.class);
		tester.assertRenderedPage(SignInPage.class);
		tester.clickLink("signin:signin:forget");
		return tester.newFormTester("forget:form");
	}

	@Test
	public void testEmptyForget() {
		FormTester formTester = showForget();
		formTester.submit("submit");
		assertEquals("There should be exactly 2 errors", 2,
				tester.getFeedbackMessages(new ExactLevelFeedbackMessageFilter(FeedbackMessage.ERROR)).size());
	}

	private String getCaptcha(String path) throws ReflectiveOperationException, SecurityException {
		// hacks with reflection
		Image captchaImg = (Image)tester.getComponentFromLastRenderedPage(path);
		Method m1 = Image.class.getDeclaredMethod("getImageResource");
		m1.setAccessible(true);
		CaptchaImageResource captcha = (CaptchaImageResource)m1.invoke(captchaImg);
		Method m2 = CaptchaImageResource.class.getDeclaredMethod("getImageData", Attributes.class);
		m2.setAccessible(true);
		m2.invoke(captcha, (Attributes)null);
		return captcha.getChallengeId();
	}

	private void testRegister(String uid) throws ReflectiveOperationException, SecurityException {
		ButtonAjaxBehavior b1 = getButtonBehavior("signin", "register");
		tester.executeBehavior(b1);
		FormTester formTester = tester.newFormTester(PATH_REGISTER);
		formTester.setValue("login", getLogin(uid));
		formTester.setValue("email", getEmail(uid));
		formTester.setValue("firstName", String.format("first%s", uid));
		formTester.setValue("lastName", String.format("last%s", uid));
		formTester.setValue("password", userpass);
		formTester.setValue("confirmPassword", userpass);
		formTester.setValue("captcha:captchaText", getCaptcha("register:form:captcha:captcha"));
		formTester.submit("submit");
		assertEquals("There should be exactly 0 errors", 0,
				tester.getFeedbackMessages(new ExactLevelFeedbackMessageFilter(FeedbackMessage.ERROR)).size());
		tester.assertLabel("register:confirmRegistration:container:message", Application.getString("warn.notverified"));
		ButtonAjaxBehavior b2 = getButtonBehavior("register:confirmRegistration", "OK");
		tester.executeBehavior(b2);
	}

	// complex test
	@Test
	public void testCompleteRegister() throws ReflectiveOperationException, SecurityException {
		// set activation properties
		List<Configuration> cfgs = cfgDao.get(CONFIG_EMAIL_AT_REGISTER, CONFIG_EMAIL_VERIFICATION);
		for (Configuration c : cfgs) {
			c.setValueB(true);
			cfgDao.update(c, null);
		}
		try {
			tester.startPage(SignInPage.class);
			tester.assertRenderedPage(SignInPage.class);

			String uid = UUID.randomUUID().toString();
			testRegister(uid);

			// activate
			User u = userDao.getByLogin(getLogin(uid), User.Type.user, null);
			assertNotNull(u);
			assertFalse(u.getRights().contains(User.Right.Login));
			tester.startPage(ActivatePage.class, new PageParameters().add(ActivatePage.ACTIVATION_PARAM, u.getActivatehash()));
			tester.assertRenderedPage(SignInPage.class);

			// check activated
			u = userDao.getByLogin(getLogin(uid), User.Type.user, null);
			assertNotNull(u);
			assertNull(u.getActivatehash());
			assertTrue(u.getRights().contains(User.Right.Login));
			checkLogin(getEmail(uid), userpass);
			tester.getSession().invalidateNow();

			// forget by 'random'
			tester.getSession().setLocale(Locale.GERMAN);
			Random rnd = new Random();
			int type = rnd.nextInt(2);
			FormTester forgetTester = showForget();
			forgetTester.select("type", type);
			forgetTester.setValue("name", type == 0 ? getEmail(uid) : getLogin(uid));
			forgetTester.setValue("captcha:captchaText", getCaptcha("forget:form:captcha:captcha"));
			forgetTester.submit("submit");
			assertEquals("There should be exactly 0 errors", 0,
					tester.getFeedbackMessages(new ExactLevelFeedbackMessageFilter(FeedbackMessage.ERROR)).size());
			tester.assertLabel("forget:confirmDialog:container:message", Application.getString("321"));

			// reset password
			u = userDao.getByEmail(getEmail(uid));
			assertNotNull(u);
			assertNotNull(u.getResethash());
			tester.startPage(ResetPage.class, new PageParameters().add(ResetPage.RESET_PARAM, u.getResethash()));
			tester.assertRenderedPage(ResetPage.class);

			// check reset
		} finally {
			for (Configuration c : cfgs) {
				c.setValueB(false);
				cfgDao.update(c, null);
			}
		}
	}
}