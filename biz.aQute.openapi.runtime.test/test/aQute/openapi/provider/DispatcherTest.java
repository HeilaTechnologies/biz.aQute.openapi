package aQute.openapi.provider;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import aQute.bnd.service.url.TaggedData;
import aQute.launchpad.Launchpad;
import aQute.launchpad.LaunchpadBuilder;
import aQute.lib.converter.Converter;
import aQute.libg.map.MAP;
import aQute.openapi.provider.OpenAPIRuntime.Configuration;
import gen.pathparams.PathparamsBase;
import gen.simple.SimpleBase;

public class DispatcherTest {
	static ScheduledExecutorService	executor	= Executors.newScheduledThreadPool(2);

	@Rule
	public OpenAPIServerTestRule	runtime		= new OpenAPIServerTestRule();
	Launchpad fw = new LaunchpadBuilder().create();

	@After
	public void close() throws Exception {
		fw.close();
	}

	@Test
	public void testNoBlockingWhenNotFound() throws Exception {
		runtime.runtime.activate(fw.getBundleContext(), getConfig(MAP.$("registerOnStart", new String[] {})));
		long start = System.currentTimeMillis();
		TaggedData go = runtime.http.build().get().asTag().go(runtime.uri.resolve("/abc/whatever"));
		assertThat(go.getResponseCode(), is(404));
		assertThat(System.currentTimeMillis() - start, lessThan(5000L));
	}

	@Test
	public void testBlockingWhenNotFound() throws Exception {
		runtime.runtime.activate(fw.getBundleContext(),
				getConfig(MAP.$("registerOnStart", (Object) new String[] { "/abc" }).$("delayOnNotFoundInSecs", 2)));
		long start = System.currentTimeMillis();
		TaggedData go = runtime.http.build().get().asTag().go(runtime.uri.resolve("/abc/whatever"));
		assertThat(go.getResponseCode(), is(404));
		assertThat(System.currentTimeMillis() - start, greaterThan(2000L));
	}

	@Test
	public void testBlockingWhenNotFoundUntilRegistered() throws Exception {
		System.out.println("testBlockingWhenNotFoundUntilRegistered");
		AtomicBoolean visited = new AtomicBoolean();

		runtime.runtime.activate(fw.getBundleContext(),
				getConfig(MAP.$("registerOnStart", (Object) new String[] { "/v1" }).$("delayOnNotFoundInSecs",
						2)));
		long start = System.currentTimeMillis();

		executor.schedule(() -> {
			System.out.println("Adding /v1");
			runtime.runtime.add(new SimpleBase() {

				@Override
				protected void simple() throws Exception {
					visited.set(true);
				}
			});
		}, 1000, TimeUnit.MILLISECONDS);

		System.out.println("Waiting for response ");
		TaggedData go = runtime.http.build().get().asTag().go(runtime.uri.resolve("/v1/simple"));
		System.out.println("Response " + go);
		assertThat(go.getResponseCode(), is(200));
		assertThat(System.currentTimeMillis() - start, lessThan(2000L));
		assertThat(visited.get(), is(true));
	}

	@Test
	public void testPathParameterOrder() throws Exception {
		runtime.runtime.activate(fw.getBundleContext(),
				getConfig(MAP.$("registerOnStart", (Object) new String[] { "/api/v1" }).$("delayOnNotFoundInSecs",
						3)));
		long start = System.currentTimeMillis();

		List<String> result = new ArrayList<String>();
		runtime.runtime.add(new PathparamsBase() {

			@Override
			protected void getmap(String a) throws Exception {
				result.add("getmap-" + a);
			}

			@Override
			protected void setmap_x(String a, String b) throws Exception {
				result.add("setmap-" + a + "-" + b);
			}

			@Override
			protected void deletemap(String a) throws Exception {
				result.add("deletemap-" + a);
			}

			@Override
			protected void setmap_foo_bar(String a) throws Exception {
				result.add("setmap_foo_bar-" + a);
			}

			@Override
			protected void setmap_bar(String a, String b) throws Exception {
				result.add("setmap_bar" + a + b);
			}

			@Override
			protected void setmap_foo(String a, String b) throws Exception {
				result.add("setmap_foo" + a + b);
			}

		});

		TaggedData get = runtime.http.build().get().asTag().go(runtime.uri.resolve("/api/v1/a/1"));
		assertThat(get.getResponseCode(), is(200));

		TaggedData delete = runtime.http.build().delete().asTag().go(runtime.uri.resolve("/api/v1/a/1"));
		assertThat(delete.getResponseCode(), is(200));

		TaggedData put = runtime.http.build().put().upload("hi").asTag().go(runtime.uri.resolve("/api/v1/a/1/2"));
		assertThat(put.getResponseCode(), is(200));

		TaggedData putfoobar = runtime.http.build().put().upload("hi").asTag().go(runtime.uri.resolve("/api/v1/a/1/foo/bar"));
		assertThat(putfoobar.getResponseCode(), is(200));

		TaggedData putfoo = runtime.http.build().put().upload("hi").asTag().go(runtime.uri.resolve("/api/v1/a/1/bar/2"));
		assertThat(putfoo.getResponseCode(), is(200));

		TaggedData putbar = runtime.http.build().put().upload("hi").asTag().go(runtime.uri.resolve("/api/v1/a/1/foo/2"));
		assertThat(putbar.getResponseCode(), is(200));

		assertThat(result, Matchers.containsInAnyOrder("getmap-1", "setmap-1-2", "deletemap-1", "setmap_foo_bar-1",
				"setmap_bar12", "setmap_foo12"));

		assertThat(System.currentTimeMillis() - start, lessThan(2000L));
	}

	private Configuration getConfig(Map<String, Object> config) throws Exception {
		return Converter.cnv(OpenAPIRuntime.Configuration.class, config);
	}

}
