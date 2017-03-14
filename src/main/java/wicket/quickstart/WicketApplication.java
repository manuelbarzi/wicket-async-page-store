package wicket.quickstart;

import org.apache.wicket.DefaultPageManagerProvider;
import org.apache.wicket.Session;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.page.CouldNotLockPageException;
import org.apache.wicket.page.PageAccessSynchronizer;
import org.apache.wicket.pageStore.IDataStore;
import org.apache.wicket.pageStore.IPageStore;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.protocol.http.WebSession;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.Response;
import org.apache.wicket.util.time.Duration;

/**
 * Application object for your web application. If you want to run this application without
 * deploying, run the Start class.
 * 
 * @see wicket.quickstart.Start#main(String[])
 * 
 * @author manuelbarzi
 */
public class WicketApplication extends WebApplication
{
	/**
	 * @see org.apache.wicket.Application#getHomePage()
	 */
	@Override
	public Class<? extends WebPage> getHomePage()
	{
		return HomePage.class;
	}

	/**
	 * @see org.apache.wicket.Application#init()
	 */
	@Override
	public void init()
	{
		super.init();

		// add your configuration here

		DefaultPageManagerProvider pageManagerProvider = new DefaultPageManagerProvider(this)
		{
			@Override
			protected IPageStore newPageStore(final IDataStore dataStore)
			{
				return new AsyncPageStore(super.newPageStore(dataStore), 100);
				// return super.newPageStore(dataStore);
			}
		};
		setPageManagerProvider(pageManagerProvider);
	}

	@SuppressWarnings("serial")
	@Override
	public Session newSession(Request request, Response response)
	{
		return new WebSession(request)
		{
			@Override
			protected PageAccessSynchronizer newPageAccessSynchronizer(Duration timeout)
			{
				// TODO Auto-generated method stub
				return new PageAccessSynchronizer(timeout)
				{
					@Override
					public void lockPage(int pageId) throws CouldNotLockPageException
					{
						System.out.println("lock " + pageId);
						super.lockPage(pageId);
					}

					@Override
					public void unlockPage(int pageId)
					{
						System.out.println("unlock " + pageId);
						super.unlockPage(pageId);
					}

					@Override
					public void unlockAllPages()
					{
						System.out.println("unlock all");
						super.unlockAllPages();
					}
				};
			}
		};
	}
}
