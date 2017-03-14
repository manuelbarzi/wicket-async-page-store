package wicket.quickstart;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.link.Link;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Home page.
 * 
 * @author manuelbarzi
 */
@SuppressWarnings("serial")
public class HomePage extends WebPage
{

	private static final Logger log = LoggerFactory.getLogger(HomePage.class);

	private String sessionId;

	/**
	 * @param parameters
	 */
	public HomePage()
	{
		super();

		sessionId = getSession().getId();

		add(new Link<Void>("link")
		{

			/**
			 * @see org.apache.wicket.markup.html.link.Link#onClick()
			 */
			@Override
			public void onClick()
			{
				setResponsePage(HomePage.class);
			}

			/**
			 * @see org.apache.wicket.markup.html.link.Link#onComponentTag(org.apache.wicket.markup.ComponentTag)
			 */
			@Override
			protected void onComponentTag(final ComponentTag tag)
			{
				tag.put("onclick",
					"document.getElementById('holder').innerHTML = 'Loading ... (slow because serialization is blocking)';return true;");
				super.onComponentTag(tag);
			}
		});
	}

	private interface Progress
	{
		void onStep(int step, int maxSteps);
	}

	private void delay(int seconds, Progress progress)
	{
		for (int i = 1; i <= seconds; i++)
		{
			try
			{
				Thread.sleep(TimeUnit.SECONDS.toMillis(1));
				progress.onStep(i, seconds);
			}
			catch (final InterruptedException e)
			{
				throw new RuntimeException(e);
			}
		}
	}

	private void writeObject(java.io.ObjectOutputStream s) throws IOException
	{
		delay(2, new Progress()
		{
			@Override
			public void onStep(int step, int maxSteps)
			{
				log.debug("serialize page {} (step {} of {}, session {})", getPageId(), step,
					maxSteps, sessionId);
			}
		});
	}

	private void readObject(java.io.ObjectInputStream s) throws IOException, ClassNotFoundException
	{
		delay(2, new Progress()
		{
			@Override
			public void onStep(int step, int maxSteps)
			{
				log.debug("deserialize page {} (step {} of {}, session {})", getPageId(), step,
					maxSteps, sessionId);
			}
		});
	}

}
