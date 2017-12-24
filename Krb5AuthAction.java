package com.sitech.bds.test.security;

import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.Lookup;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.impl.auth.SPNegoSchemeFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

/**
 * 
 * @author An Liang
 *
 */
public class Krb5AuthAction {

	private String principal;
    private String keyTabLocation;
    
    public Krb5AuthAction(){
    	
    }
    
    public Krb5AuthAction(String principal, String keyTabLocation) {
        super();
        this.principal = principal;
        this.keyTabLocation = keyTabLocation;
    }
    
    public Krb5AuthAction(String principal, String keyTabLocation, boolean isDebug) {
        this(principal, keyTabLocation);
        if (isDebug) {
            System.setProperty("sun.security.spnego.debug", "true");
            System.setProperty("sun.security.krb5.debug", "true");
        }
    }
    
    public Krb5AuthAction(String principal, String keyTabLocation, String krb5Location, boolean isDebug) {
        this(principal, keyTabLocation, isDebug);
        System.setProperty("java.security.krb5.conf", krb5Location);
    }
    
    @SuppressWarnings("unused")
	private static HttpClient buildSpengoHttpClient() {
        HttpClientBuilder builder = HttpClientBuilder.create();
        Lookup<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create().
                register(AuthSchemes.SPNEGO, new SPNegoSchemeFactory(true)).build();
        builder.setDefaultAuthSchemeRegistry(authSchemeRegistry);
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(null, -1, null), new Credentials() {
            @Override
            public Principal getUserPrincipal() {
                return null;
            }
            @Override
            public String getPassword() {
                return null;
            }
        });
        builder.setDefaultCredentialsProvider(credentialsProvider);
        CloseableHttpClient httpClient = builder.build();
        return httpClient;
    }
    
    public HttpResponse callRestUrl(final String url,final String userId) {
        Configuration config = new Configuration() {
            @SuppressWarnings("serial")
            @Override
            public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
                return new AppConfigurationEntry[] { new AppConfigurationEntry("com.sun.security.auth.module.Krb5LoginModule",
                        AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, new HashMap<String, Object>() {
                    {
                        put("useTicketCache", "true");
                        put("useKeyTab", "true");
                        put("keyTab", keyTabLocation);
                        put("refreshKrb5Config", "true");
                        put("principal", principal);
                        put("storeKey", "true");
                        put("doNotPrompt", "true");
                        put("isInitiator", "true");
                        put("debug", "true");
                    }
                }) };
            }
        };
        Set<Principal> princ = new HashSet<Principal>(1);
        princ.add(new KerberosPrincipal(userId));
        Subject sub = new Subject(false, princ, new HashSet<Object>(), new HashSet<Object>());
        try {
            LoginContext lc = new LoginContext("Krb5Login", sub, null, config);
            lc.login();
            Subject serviceSubject = lc.getSubject();
            return Subject.doAs(serviceSubject, new PrivilegedAction<HttpResponse>() {
                HttpResponse httpResponse = null;
                @Override
                public HttpResponse run() {
                    try {
                        HttpUriRequest request = new HttpGet(url);

                        HttpClient spnegoHttpClient = buildSpengoHttpClient();
                        httpResponse = spnegoHttpClient.execute(request);
                        return httpResponse;
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                    return httpResponse;
                }
            });
        } catch (Exception le) {
            le.printStackTrace();
        }
        return null;
    }
    
    public static void main(String[] args) throws UnsupportedOperationException, IOException {
    	System.setProperty("user.name","oozieweb@ADSERV.COM");
        String user ="oozieweb@ADSERV.COM";
        String keytab="D:\\Documents\\oozieweb\\oozieweb.keytab";
        String krb5Location="D:\\Documents\\oozieweb\\conf\\krb5.conf";
        Krb5AuthAction restTest = new Krb5AuthAction(user,keytab,krb5Location, false);
        String url="http://172.19.149.50:13099/region";
        HttpResponse response = restTest.callRestUrl(url,user);
        InputStream is = response.getEntity().getContent();
        System.out.println("Status code " + response.getStatusLine().getStatusCode());
        System.out.println("message is :"+Arrays.deepToString(response.getAllHeaders()));
        System.out.println("字符串：\n"+new String(IOUtils.toByteArray(is), "UTF-8"));
    }
}
