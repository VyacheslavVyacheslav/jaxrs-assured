package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions;

import io.github.vyacheslavvyacheslav.jaxrsassured.core.clients.ConfigurationResource;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.response.AssuredResponse;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.util.HttpClientUtil;
import io.restassured.path.json.JsonPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * setPathParam и параметры пути локаторов — на уровне сети: проверяется сырой путь, который получил WireEchoServer.
 */
class PathParamSettingWireTest {

    private final WireEchoServer server = new WireEchoServer();
    private final ResteasyClient client = HttpClientUtil.restEasyClient();

    PathParamSettingWireTest() throws IOException {
    }

    @AfterEach
    void close() {
        client.close();
        server.close();
    }

    @Test
    void locatorValuesShouldResolvePathWithoutSettings() {
        assertEquals("/items/7/details", path(resource().getItemResource(7).details()));
        assertEquals("/items/7/parts/p1", path(resource().getItemResource(7).getPartResource("p1").get()));
    }

    @Test
    void locatorValueShouldBeConvertedAsBefore() {
        assertEquals("/formats/e_book/books", path(resource().getFormatResource(Format.E_BOOK).books()));
    }

    @Test
    void setPathParamShouldReplaceMethodPathParam() {
        assertEquals("/items/abc", path(resource().setPathParam("id", "abc").findOne(7)));
    }

    @Test
    void setPathParamInsideAssuredShouldApplyOnlyToThisCall() {
        ItemsResource resource = resource();

        String inside = resource.assured(r -> r.setPathParam("id", "abc").findOne(7)).extract().asString();
        String after = resource.findOne(7).extract().asString();

        assertEquals("/items/abc", JsonPath.from(inside).getString("path"));
        assertEquals("/items/7", JsonPath.from(after).getString("path"));
    }

    @Test
    void setPathParamShouldReplaceLocatorValue() {
        assertEquals("/items/abc/details", path(resource().getItemResource(7).setPathParam("id", "abc").details()));
    }

    @Test
    void setPathParamShouldBeInheritedBySubResources() {
        PartResource part = resource().setPathParam("id", "abc").getItemResource(7).getPartResource("p1");

        assertEquals("/items/abc/parts/p1", path(part.get()));
    }

    @Test
    void setPathParamShouldReplaceOnlyNamedParam() {
        PartResource part = resource().getItemResource(7).getPartResource("p1").setPathParam("partId", -1);

        assertEquals("/items/7/parts/-1", path(part.get()));
    }

    @Test
    void resetShouldRestoreLocatorValues() {
        PartResource part = resource().getItemResource(7).getPartResource("p1")
                .setPathParam("id", "abc").setPathParam("partId", "x");

        part.reset();

        assertEquals("/items/7/parts/p1", path(part.get()));
    }

    @Test
    void setPathParamWithoutTemplateInPathShouldNotChangeCall() {
        ItemsResource resource = resource().setPathParam("id", "abc").setPathParam("unknown", "x");

        assertEquals("/items", path(resource.list()));
        assertEquals("/items/abc", path(resource.findOne(7)));
    }

    @Test
    void setPathParamValueShouldBeEncodedAsPathSegment() {
        assertEquals("/items/a%20b%2Fc", path(resource().setPathParam("id", "a b/c").findOne(7)));
    }

    @Test
    void repeatedSetPathParamShouldReplacePreviousValue() {
        ItemsResource resource = resource().setPathParam("id", "first").setPathParam("id", "second");

        assertEquals("/items/second", path(resource.findOne(7)));
        assertEquals("ItemsResource [path:id=second]", resource.toString());
    }

    @Test
    void nullLocatorValueShouldFailAtLocatorCall() {
        NullPointerException failure = assertThrows(NullPointerException.class,
                () -> resource().getItemResource(null));

        assertTrue(failure.getMessage().contains("id"), failure.getMessage());
    }

    private ItemsResource resource() {
        return ((AssuredClientWebTarget) client.target(server.baseUri())).proxy(ItemsResource.class);
    }

    private static String path(AssuredResponse response) {
        return JsonPath.from(response.extract().asString()).getString("path");
    }

    /** Значение в пути — из toString, как у сгенерированных энумов. */
    enum Format {
        E_BOOK;

        @Override
        public String toString() {
            return "e_book";
        }
    }

    interface ItemsResource extends ConfigurationResource<ItemsResource> {

        @GET
        @Path("items")
        AssuredResponse list();

        @GET
        @Path("items/{id}")
        AssuredResponse findOne(@PathParam("id") Integer id);

        @Path("items/{id}")
        ItemResource getItemResource(@PathParam("id") Integer id);

        @Path("formats/{format}")
        FormatResource getFormatResource(@PathParam("format") Format format);
    }

    interface ItemResource extends ConfigurationResource<ItemResource> {

        @GET
        @Path("details")
        AssuredResponse details();

        @Path("parts/{partId}")
        PartResource getPartResource(@PathParam("partId") String partId);
    }

    interface PartResource extends ConfigurationResource<PartResource> {

        @GET
        AssuredResponse get();
    }

    interface FormatResource extends ConfigurationResource<FormatResource> {

        @GET
        @Path("books")
        AssuredResponse books();
    }
}
