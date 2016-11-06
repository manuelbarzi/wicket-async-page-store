package wicket.quickstart;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.request.mapper.parameter.PageParameters;

/**
 * Home page.
 * 
 * @author manuelbarzi
 */
public class HomePage extends WebPage {
	private static final long serialVersionUID = 1L;

	/**
	 * @param parameters
	 */
	@SuppressWarnings("serial")
	public HomePage(final PageParameters parameters) {
		super(parameters);
		add(new Link<Void>("link") {

			/**
			 * @see org.apache.wicket.markup.html.link.Link#onClick()
			 */
			@Override
			public void onClick() {
				setResponsePage(HomePage.class);
			}

			/**
			 * @see org.apache.wicket.markup.html.link.Link#onComponentTag(org.apache.wicket.markup.ComponentTag)
			 */
			@Override
			protected void onComponentTag(final ComponentTag tag) {
				tag.put("onclick",
						"document.getElementById('holder').innerHTML = 'Loading ... (slow because serialization is blocking)';return true;");
				super.onComponentTag(tag);
			}
		});
	}

	/**
	 * @param s
	 * @throws IOException
	 */
	private void writeObject(java.io.ObjectOutputStream s) throws IOException {
		count("serialize " + getPageId());
	}
	
	private void count(String title) {
		int total = 5;
		for (int i = 0; i < total; i++) {
			try {
				Thread.sleep(TimeUnit.SECONDS.toMillis(1));
				System.out.println(title + " (step " + (i + 1) + " of " + total + ")");
			} catch (final InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private void readObject(java.io.ObjectInputStream s) throws IOException, ClassNotFoundException {
		count("deserialize " + getPageId());
	}
}
