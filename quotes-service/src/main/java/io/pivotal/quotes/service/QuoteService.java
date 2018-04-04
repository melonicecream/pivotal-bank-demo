package io.pivotal.quotes.service;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import io.pivotal.quotes.domain.*;
import io.pivotal.quotes.exception.SymbolNotFoundException;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A service to retrieve Company and Quote information.
 * 
 * @author David Ferreira Pinto
 *
 */
@Service
@RefreshScope
@Slf4j
public class QuoteService {

	@Value("${pivotal.quotes.quotes_url}")
	protected String quote_url;

	@Value("${pivotal.quotes.companies_url}")
	protected String company_url;

	//TODO: Remove API KEY
	@Value("${pivotal.quotes.alpha_advantage_rest_query}")
	protected String alpha_advantage_url = "https://www.alphavantage.co/query?function=BATCH_STOCK_QUOTES&symbols={symbols}&apikey=B1SQNYULIQ8J9X2A";

	public static final String FMT = "json";

	/*
         * cannot autowire as don't want ribbon here.
	 */
	private RestTemplate restTemplate = new RestTemplate();

	/**
	 * Retrieves an up to date quote for the given symbol.
	 * 
	 * @param symbol
	 *            The symbol to retrieve the quote for.
	 * @return The quote object or null if not found.
	 * @throws SymbolNotFoundException
	 */
	@HystrixCommand(fallbackMethod = "getQuoteFallback")
	public Quote getQuote(String symbol) throws SymbolNotFoundException {
		log.debug("QuoteService.getQuote: retrieving quote for: " + symbol);
		Map<String, String> params = new HashMap<String, String>();
		params.put("symbol", symbol);

		Quote quote = restTemplate.getForObject(quote_url, Quote.class, params);
		log.debug("QuoteService.getQuote: retrieved quote: " + quote);

		if (quote.getSymbol() == null) {
			throw new SymbolNotFoundException("Symbol not found: " + symbol);
		}
		return quote;
	}

	@SuppressWarnings("unused")
	private Quote getQuoteFallback(String symbol)
			throws SymbolNotFoundException {
		log.debug("QuoteService.getQuoteFallback: circuit opened for symbol: "
				+ symbol);
		Quote quote = new Quote();
		quote.setSymbol(symbol);
		quote.setStatus("FAILED");
		return quote;
	}

	/**
	 * Retrieves a list of CompanyInfor objects. Given the name parameters, the
	 * return list will contain objects that match the search both on company
	 * name as well as symbol.
	 * 
	 * @param name
	 *            The search parameter for company name or symbol.
	 * @return The list of company information.
	 */
	@HystrixCommand(fallbackMethod = "getCompanyInfoFallback",
		    commandProperties = {
		      @HystrixProperty(name="execution.timeout.enabled", value="false")
		    })
	public List<CompanyInfo> getCompanyInfo(String name) {
		log.debug("QuoteService.getCompanyInfo: retrieving info for: "
				+ name);
		Map<String, String> params = new HashMap<String, String>();
		params.put("name", name);
		CompanyInfo[] companies = restTemplate.getForObject(company_url,
				CompanyInfo[].class, params);
		log.debug("QuoteService.getCompanyInfo: retrieved info: "
				+ companies);
		return Arrays.asList(companies);
	}

	/**
	 * Retrieve multiple quotes at once.
	 * 
	 * @param symbols
	 *            comma delimeted list of symbols.
	 * @return a list of quotes.
	 */
	public List<Quote> getQuotes(String symbols) {
		log.debug("retrieving multiple quotes for: " + symbols);
		log.debug("alpha advantage URL: " + alpha_advantage_url);
		AlphaAdvantageResponse response = restTemplate.getForObject(alpha_advantage_url, AlphaAdvantageResponse.class, symbols);
		AlphaAdvantageQuote n = new AlphaAdvantageQuote();
		log.debug("Got response: " + response);
		List<Quote> quotes = response
				.getQuotes()
				.stream()
				.map(aaQuote -> QuoteMapper.INSTANCE.mapFromAlphaAdvantageQuote(aaQuote))
				.collect(Collectors.toList());
		return quotes;
	}


	@SuppressWarnings("unused")
	private List<CompanyInfo> getCompanyInfoFallback(String symbol)
			throws SymbolNotFoundException {
		log.debug("QuoteService.getCompanyInfoFallback: circuit opened for symbol: "
				+ symbol);
		List<CompanyInfo> companies = new ArrayList<>();
		return companies;
	}
}