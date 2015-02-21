package com.techlooper.service;

import com.techlooper.pojo.GravatarModel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Created by chrisshayan on 2/21/15.
 */
public class GravatarServiceTest {
    public static final String EMAIL = "hamedshayan@gmail.com";

    @Test
    public void getMD5Hash() {
        final String myEmailHash = "41d0bb0711c4102dc381d5e731973d2c"; // Generated by http://www.md5.cz/
        GravatarService gravatarService = new GravatarService();
        assertEquals(myEmailHash, gravatarService.getMD5Hash(EMAIL));
    }

    @Test
    public void testFindGravatarProfile() throws Exception {
        GravatarService gravatarService = new GravatarService();
        final GravatarModel gravatarProfile = gravatarService.findGravatarProfile(EMAIL);
        assertNotNull(gravatarProfile);

        final String myProfileUrl = "http://gravatar.com/shayanchris";
        final String myThumbnailUrl = "https://secure.gravatar.com/avatar/41d0bb0711c4102dc381d5e731973d2c";
        assertEquals(myProfileUrl, gravatarProfile.getProfileUrl());
        assertEquals(myThumbnailUrl, gravatarProfile.getThumbnailUrl());
    }

}
