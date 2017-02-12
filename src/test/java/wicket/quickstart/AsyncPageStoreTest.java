package wicket.quickstart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.RandomUtils;
import org.apache.wicket.page.IManageablePage;
import org.apache.wicket.pageStore.DefaultPageStore;
import org.apache.wicket.pageStore.DiskDataStore;
import org.apache.wicket.pageStore.IDataStore;
import org.apache.wicket.pageStore.IPageStore;
import org.apache.wicket.serialize.ISerializer;
import org.apache.wicket.serialize.java.DeflatedJavaSerializer;
import org.apache.wicket.util.file.File;
import org.apache.wicket.util.lang.Bytes;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

/**
 * AsyncPageStoreTest
 * 
 * @author manuelbarzi
 *
 */
public class AsyncPageStoreTest {

	/** Log for reporting. */
	private static final Logger log = LoggerFactory.getLogger(AsyncPageStoreTest.class);

	private interface IManageablePageExtended extends IManageablePage {
		String getSessionId();
	}

	@SuppressWarnings("serial")
	private static class DummyPage implements IManageablePageExtended {

		private int id;
		private long writeMillis;
		private long readMillis;
		private String sessionId;

		private DummyPage(int id, long writeMillis, long readMillis, String sessionId) {
			this.id = id;
			this.writeMillis = writeMillis;
			this.readMillis = readMillis;
			this.sessionId = sessionId;
		}

		@Override
		public boolean isPageStateless() {
			return false;
		}

		@Override
		public int getPageId() {
			return id;
		}

		@Override
		public String getSessionId() {
			return sessionId;
		}

		@Override
		public void detach() {
		}

		@Override
		public boolean setFreezePageId(boolean freeze) {
			return false;
		}

		/**
		 * @param s
		 * @throws IOException
		 */
		private void writeObject(java.io.ObjectOutputStream s) throws IOException {
			log.debug("serializing page {} for {}ms (session {})", getPageId(), writeMillis, sessionId);
			try {
				Thread.sleep(writeMillis);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}

			s.writeInt(id);
			s.writeLong(readMillis);
			s.writeLong(writeMillis);
			s.writeObject(sessionId);
		}

		private void readObject(java.io.ObjectInputStream s) throws IOException, ClassNotFoundException {
			log.debug("deserializing page {} for {}ms (session {})", getPageId(), writeMillis, sessionId);
			try {
				Thread.sleep(readMillis);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}

			id = s.readInt();
			readMillis = s.readLong();
			writeMillis = s.readLong();
			sessionId = (String) s.readObject();
		}
	}

	@Test
	public void fullyAsyncWhenNotOverpassingStoreCapacity() throws InterruptedException {
		int sessions = 2;
		int pages = 5;
		long writeMillis = 2000;
		long readMillis = 1500;
		int asyncPageStoreCapacity = pages * sessions;

		List<Metrics> results = runTest(sessions, pages, writeMillis, readMillis, asyncPageStoreCapacity);
		
		log.debug("metrics {}", results);

		for (Metrics metrics : results) {
			assertEquals(metrics.storedPage, metrics.restoredPage);
			assertTrue(metrics.storingMillis < writeMillis);
			assertTrue(metrics.restoringMillis < readMillis);
		}
	}

	@Test
	public void syncWhenOverpassingStoreCapacity() throws InterruptedException {
		int sessions = 2;
		int pages = 5;
		long writeMillis = 2000;
		long readMillis = 1500;
		int asyncPageStoreCapacity = pages; // only up to the first round of
											// pages

		List<Metrics> results = runTest(sessions, pages, writeMillis, readMillis, asyncPageStoreCapacity);

		log.debug("metrics {}", results);

		int syncStoredCountByTiming = 0;
		int syncStoredCountByHash = 0;

		for (int i = 0; i < results.size(); i++) {
			Metrics metrics = results.get(i);

			assertEquals(metrics.storedPage.getSessionId(), metrics.restoredPage.getSessionId());
			assertEquals(metrics.storedPage.getPageId(), metrics.restoredPage.getPageId());

			boolean syncFound = false;

			if (metrics.storingMillis >= writeMillis || metrics.restoringMillis >= readMillis) {
				syncStoredCountByTiming++;
				syncFound |= true;
			}

			if (!metrics.storedPage.equals(metrics.restoredPage)) {
				syncStoredCountByHash++;
				syncFound |= true;
			}

			if (syncFound) {
				assertTrue(syncStoredCountByTiming == syncStoredCountByHash);
			}
		}

		assertTrue(syncStoredCountByTiming > 0);
		assertTrue(syncStoredCountByHash > 0);
		assertTrue(syncStoredCountByTiming == syncStoredCountByHash);
	}

	// test run

	private class Metrics {
		private IManageablePageExtended storedPage;
		private long storingMillis;
		private IManageablePageExtended restoredPage;
		private long restoringMillis;

		public String toString() {
			return "Metrics[storedPage = " + storedPage + ", storingMillis = " + storingMillis + ", restoredPage = "
					+ restoredPage + ", restoringMillis = " + restoringMillis + "]";
		}
	}

	private List<Metrics> runTest(int sessions, int pages, long writeMillis, long readMillis,
			int asyncPageStoreCapacity) throws InterruptedException {

		List<Metrics> results = new ArrayList<>();

		final CountDownLatch lock = new CountDownLatch(pages * sessions);

		ISerializer serializer = new DeflatedJavaSerializer("applicationKey");
		// ISerializer serializer = new DummySerializer();
		IDataStore dataStore = new DiskDataStore("applicationName", new File("./target"), Bytes.bytes(10000l));
		IPageStore pageStore = new DefaultPageStore(serializer, dataStore, 0) {
			// IPageStore pageStore = new DummyPageStore(new
			// File("target/store")) {

			@Override
			public void storePage(String sessionId, IManageablePage page) {

				super.storePage(sessionId, page);

				lock.countDown();
			}
		};

		IPageStore asyncPageStore = new AsyncPageStore(pageStore, asyncPageStoreCapacity);

		Stopwatch stopwatch = Stopwatch.createUnstarted();

		for (int pageId = 1; pageId <= pages; pageId++) {
			for (int sessionId = 1; sessionId <= sessions; sessionId++) {
				String session = String.valueOf(sessionId);
				Metrics metrics = new Metrics();

				stopwatch.reset();
				IManageablePageExtended page = new DummyPage(pageId, around(writeMillis), around(readMillis), session);
				stopwatch.start();
				asyncPageStore.storePage(session, page);
				metrics.storedPage = page;
				metrics.storingMillis = stopwatch.elapsed(TimeUnit.MILLISECONDS);

				stopwatch.reset();
				stopwatch.start();
				metrics.restoredPage = IManageablePageExtended.class.cast(asyncPageStore.getPage(session, pageId));
				metrics.restoringMillis = stopwatch.elapsed(TimeUnit.MILLISECONDS);

				results.add(metrics);
			}
		}

		lock.await(pages * sessions * (writeMillis + readMillis), TimeUnit.MILLISECONDS);

		return results;
	}

	private long around(long target) {
		return RandomUtils.nextLong((long) (target * .9), (long) (target * 1.1));
	}

	// other aux dummy impls for testing

	@SuppressWarnings("unused")
	private class DummySerializer implements ISerializer {

		@Override
		public byte[] serialize(Object obj) {
			ByteArrayOutputStream bos = null;
			ObjectOutput out = null;
			try {
				bos = new ByteArrayOutputStream();
				out = new ObjectOutputStream(bos);
				out.writeObject(obj);
				return bos.toByteArray();
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			} catch (IOException e) {
				throw new RuntimeException(e);
			} finally {
				try {
					if (bos != null)
						bos.close();
					if (out != null)
						out.close();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}

		@Override
		public Object deserialize(byte[] bytes) {
			ByteArrayInputStream bis = null;
			ObjectInput in = null;
			try {
				bis = new ByteArrayInputStream(bytes);
				in = new ObjectInputStream(bis);
				return in.readObject();
			} catch (IOException e) {
				throw new RuntimeException(e);
			} catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			} finally {
				try {
					if (bis != null)
						bis.close();
					if (in != null)
						in.close();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}

	}

	@SuppressWarnings("unused")
	private class DummyPageStore implements IPageStore {

		private File folder;

		private DummyPageStore(File folder) {
			folder.mkdirs();
			this.folder = folder;
		}

		private File getPageFile(String sessionId, int pageId) {
			return new File(folder.getAbsolutePath() + "/" + sessionId + "-" + pageId + ".page");
		}

		private void toFile(Object obj, File file) {
			FileOutputStream fos = null;
			ObjectOutput oo = null;
			try {
				fos = new FileOutputStream(file);
				oo = new ObjectOutputStream(fos);
				oo.writeObject(obj);
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			} catch (IOException e) {
				throw new RuntimeException(e);
			} finally {
				try {
					if (fos != null)
						fos.close();
					if (oo != null)
						oo.close();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}

		private Object fromFile(File file) {
			FileInputStream fis = null;
			ObjectInput oi = null;
			try {
				fis = new FileInputStream(file);
				oi = new ObjectInputStream(fis);
				return oi.readObject();
			} catch (IOException e) {
				throw new RuntimeException(e);
			} catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			} finally {
				try {
					if (fis != null)
						fis.close();
					if (oi != null)
						oi.close();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}

		@Override
		public void destroy() {
		}

		@Override
		public IManageablePage getPage(String sessionId, int pageId) {
			return (IManageablePage) fromFile(getPageFile(sessionId, pageId));
		}

		@Override
		public void removePage(String sessionId, int pageId) {
		}

		@Override
		public void storePage(String sessionId, IManageablePage page) {
			toFile(page, getPageFile(sessionId, page.getPageId()));
		}

		@Override
		public void unbind(String sessionId) {
		}

		@Override
		public Serializable prepareForSerialization(String sessionId, Serializable page) {
			return null;
		}

		@Override
		public Object restoreAfterSerialization(Serializable serializable) {
			return null;
		}

		@Override
		public IManageablePage convertToPage(Object page) {
			return null;
		}
	}

}
