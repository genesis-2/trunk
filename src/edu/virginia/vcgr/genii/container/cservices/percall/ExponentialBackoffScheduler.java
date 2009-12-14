package edu.virginia.vcgr.genii.container.cservices.percall;

import java.util.Calendar;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * A type of scheduler that implements an exponential backoff on scheduling
 * (with the possibility of a random jitter or twitter added to the schedule).
 * 
 * @author morgan
 */
public class ExponentialBackoffScheduler implements AttemptScheduler
{
	static final long serialVersionUID = 0L;
	
	static private Random TWITTER_GENERATOR = new Random();
	
	private Calendar _lifetime = null;
	private Integer _maxFailedAttempts = null;
	private long _backoffBase;
	private Long _backoffTwitterBase = null;
	private Integer _exponentAttemptCap = null;
	
	public ExponentialBackoffScheduler(
		Calendar lifetime, Integer maxFailedAttempts,
		Integer exponentAttemptCap,
		long backoffBase, TimeUnit backoffBaseUnits,
		Long backoffTwitterBase, TimeUnit backoffTwitterBaseUnits)
	{
		if (backoffBaseUnits == null)
			backoffBaseUnits = TimeUnit.MILLISECONDS;
		
		if (backoffTwitterBaseUnits == null)
			backoffTwitterBaseUnits = TimeUnit.MILLISECONDS;
		
		_exponentAttemptCap = exponentAttemptCap;
		_lifetime = lifetime;
		_maxFailedAttempts = maxFailedAttempts;
		_backoffBase = TimeUnit.MILLISECONDS.convert(backoffBase,
			backoffBaseUnits);
		_backoffTwitterBase = (backoffTwitterBase == null) ? null :
			TimeUnit.MILLISECONDS.convert(backoffTwitterBase,
				backoffTwitterBaseUnits);
	}
	
	public ExponentialBackoffScheduler(
		long lifetime, TimeUnit lifetimeUnits, Integer maxFailedAttempts,
		Integer exponentAttemptCap,
		long backoffBase, TimeUnit backoffBaseUnits,
		Long backoffTwitterBase, TimeUnit backoffTwitterBaseUnits)
	{
		if (backoffBaseUnits == null)
			backoffBaseUnits = TimeUnit.MILLISECONDS;
		
		if (backoffTwitterBaseUnits == null)
			backoffTwitterBaseUnits = TimeUnit.MILLISECONDS;
		
		if (lifetimeUnits == null)
			lifetimeUnits = TimeUnit.MILLISECONDS;
		
		_lifetime = Calendar.getInstance();
		_lifetime.setTimeInMillis(System.currentTimeMillis() +
			TimeUnit.MILLISECONDS.convert(lifetime, lifetimeUnits));
		
		_exponentAttemptCap = exponentAttemptCap;
		_maxFailedAttempts = maxFailedAttempts;
		_backoffBase = TimeUnit.MILLISECONDS.convert(
			backoffBase, backoffBaseUnits);
		_backoffTwitterBase = (backoffTwitterBase == null) ? null :
			TimeUnit.MILLISECONDS.convert(backoffTwitterBase, backoffTwitterBaseUnits);
	}
	
	@Override
	final public Calendar nextAttempt(Calendar now, int numFailedAttempts)
	{
		int countedFailedAttempts = numFailedAttempts;
		if (_exponentAttemptCap != null && 
			(countedFailedAttempts > _exponentAttemptCap))
				countedFailedAttempts = _exponentAttemptCap;
		
		if (_lifetime != null && now.after(_lifetime))
			return null;
		if (_maxFailedAttempts != null && numFailedAttempts >= _maxFailedAttempts)
			return null;
		
		long delay = _backoffBase << (countedFailedAttempts - 1);
		if (_backoffTwitterBase != null)
		{
			long twitterRange = _backoffTwitterBase << (countedFailedAttempts);
			long twitter = (TWITTER_GENERATOR.nextLong() & Long.MAX_VALUE) %
				twitterRange;
			delay += (twitter - (twitterRange >> 1));
		}
		
		if (delay < 0L)
			delay = 0L;
		
		Calendar next = Calendar.getInstance();
		next.setTimeInMillis(now.getTimeInMillis() + delay);
		return next;
	}
}