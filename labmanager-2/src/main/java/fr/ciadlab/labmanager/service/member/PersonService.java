/*
 * $Id$
 * 
 * Copyright (c) 2019-22, CIAD Laboratory, Universite de Technologie de Belfort Montbeliard
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of the Systems and Transportation Laboratory ("Confidential Information").
 * You shall not disclose such Confidential Information and shall use
 * it only in accordance with the terms of the license agreement
 * you entered into with the CIAD.
 * 
 * http://www.ciad-lab.fr/
 */

package fr.ciadlab.labmanager.service.member;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.google.common.base.Strings;
import fr.ciadlab.labmanager.entities.member.Person;
import fr.ciadlab.labmanager.entities.publication.Authorship;
import fr.ciadlab.labmanager.entities.publication.Publication;
import fr.ciadlab.labmanager.repository.member.PersonRepository;
import fr.ciadlab.labmanager.repository.publication.AuthorshipRepository;
import fr.ciadlab.labmanager.repository.publication.PublicationRepository;
import fr.ciadlab.labmanager.service.AbstractService;
import fr.ciadlab.labmanager.utils.names.PersonNameComparator;
import fr.ciadlab.labmanager.utils.names.PersonNameParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Service for managing the persons.
 * 
 * @author $Author: sgalland$
 * @author $Author: tmartine$
 * @version $Name$ $Revision$ $Date$
 * @mavengroupid $GroupId$
 * @mavenartifactid $ArtifactId$
 */
@Service
public class PersonService extends AbstractService {

	private PublicationRepository publicationRepository;

	private AuthorshipRepository authorshipRepository;

	private PersonRepository personRepository;

	private PersonNameParser nameParser;

	private PersonNameComparator nameComparator;

	/** Constructor for injector.
	 * This constructor is defined for being invoked by the IOC injector.
	 *
	 * @param publicationRepository the publication repository.
	 * @param authorshipRepository the authorship repository.
	 * @param personRepository the person repository.
	 * @param nameParser the parser of person names.
	 * @param nameComparator the comparator of person names.
	 */
	public PersonService(@Autowired PublicationRepository publicationRepository,
			@Autowired AuthorshipRepository authorshipRepository,
			@Autowired PersonRepository personRepository,
			@Autowired PersonNameParser nameParser,
			@Autowired PersonNameComparator nameComparator) {
		this.publicationRepository = publicationRepository;
		this.authorshipRepository = authorshipRepository;
		this.personRepository = personRepository;
		this.nameParser = nameParser;
		this.nameComparator = nameComparator;
	}

	/** Replies the list of all the persons from the database.
	 *
	 * @return all the persons.
	 */
	public List<Person> getAllPersons() {
		return this.personRepository.findAll();
	}

	/** Replies the person with the given identifier.
	 *
	 * @param identifier the identifier of the person.
	 * @return the person, or {@code null} if none.
	 */
	public Person getPerson(int identifier) {
		final Optional<Person> byId = this.personRepository.findById(Integer.valueOf(identifier));
		return byId.orElse(null);
	}

	/** Create a person in the database.
	 *
	 * @param firstName the first name of the person.
	 * @param lastName the last name of the person.
	 * @param email the email of the person.
	 * @return the identifier of the person in the database.
	 */
	public int createPerson(String firstName, String lastName, String email) {
		final Person res = new Person();
		res.setFirstName(firstName);
		res.setLastName(lastName);
		res.setEmail(email);
		this.personRepository.save(res);

		return res.getId();
	}

	/** Change the attributes of a person in the database.
	 *
	 * @param identifier the identifier of the person in the database.
	 * @param firstName the first name of the person.
	 * @param lastName the last name of the person.
	 * @param email the email of the person.
	 */
	public void updatePerson(int identifier, String firstName, String lastName, String email) {
		final Optional<Person> res = this.personRepository.findById(Integer.valueOf(identifier));
		if (res.isPresent()) {
			final Person person = res.get();
			if (!Strings.isNullOrEmpty(firstName)) {
				person.setFirstName(firstName);
			}
			if (!Strings.isNullOrEmpty(lastName)) {
				person.setLastName(lastName);
			}
			person.setEmail(email);
			this.personRepository.save(person);
		}
	}

	/** Remove the person with the given identifier from the database.
	 *
	 * @param identifier the identifier of the person in the database.
	 */
	public void removePerson(int identifier) {
		final Integer id = Integer.valueOf(identifier);
		final Optional<Person> optPerson = this.personRepository.findById(id);
		if (optPerson.isPresent()) {
			final Person person = optPerson.get();
			//When removing an author, reduce the ranks of the other authorships for the pubs he made.
			for (final Authorship authorship : person.getPublications()) {
				final int rank = authorship.getAuthorRank();
				final Optional<Publication> optPub = this.publicationRepository.findById(
						Integer.valueOf(authorship.getPublication().getId()));
				if (optPub.isPresent()) {
					final Publication publication = optPub.get();
					for (final Authorship otherAuthorsips : publication.getAuthorships()) {
						final int oRank = otherAuthorsips.getAuthorRank();
						if (oRank > rank) {
							otherAuthorsips.setAuthorRank(oRank);
						}
						this.authorshipRepository.save(otherAuthorsips);
					}
				}
			}
			// membership and autorship are deleted by cascade
			this.personRepository.deleteById(id);
		}
	}

	/** Replies the database identifier for the person with the last name and first name.
	 * If there is multiple persons with the same last name and first name, one is replied.
	 * <p>The name matching is exact, i.e., the equality test is used for selecting the persons.
	 * For using a similarity test on the names, see {@link #getPersonIdBySimilarName(String, String)}.
	 *
	 * @param firstName the first name of the person.
	 * @param lastName the last name of the person.
	 * @return the identifier, or {@code 0} if no person has the given name.
	 * @see #getPersonIdBySimilarName(String, String)
	 */
	public int getPersonIdByName(String firstName, String lastName) {
		final Set<Person> res = this.personRepository.findByFirstNameAndLastName(firstName, lastName);
		if (res.size() > 0) {
			return res.iterator().next().getId();
		}
		return 0;
	}

	/** Replies the database identifier for the person with a similar name to the givan last name and givan first name.
	 * If there is multiple persons with similar last name and first name, one is replied.
	 * <p>The name matching is based on similarity of names.
	 * For using a strict equality test on the names, see {@link #getPersonIdByName(String, String)}.
	 *
	 * @param firstName the first name of the person.
	 * @param lastName the last name of the person.
	 * @return the identifier, or {@code 0} if no person has the given name.
	 * @see #getPersonIdByName(String, String)
	 */
	public int getPersonIdBySimilarName(String firstName, String lastName) {
		final Person person = getPersonBySimilarName(firstName, lastName);
		if (person != null) {
			return person.getId();
		}
		return 0;
	}

	/** Replies the person with a similar name to the givan last name and givan first name.
	 * If there is multiple persons with similar last name and first name, one is replied.
	 * <p>The name matching is based on similarity of names.
	 * For using a strict equality test on the names, see {@link #getPersonIdByName(String, String)}.
	 *
	 * @param firstName the first name of the person.
	 * @param lastName the last name of the person.
	 * @return the person, or {@code null} if no person has the given name.
	 * @see #getPersonIdBySimilarName(String, String)
	 */
	public Person getPersonBySimilarName(String firstName, String lastName) {
		for (final Person person : this.personRepository.findAll()) {
			if (this.nameComparator.isSimilar(firstName, lastName, person.getFirstName(), person.getLastName())) {
				return person;
			}
		}
		return null;
	}

	/** Extract the list of the authors.
	 * <p>The format of the list of authors follows the rules of {@link PersonNameParser}.
	 * <p>The returned authors are not saved into the database. It means that if the given
	 * list of authors contains a known author, this person is read from the database.
	 * If the author is unknown, a {@link Person} object is created but not saved into the
	 * database.
	 * 
	 * @param authorText the list of authors to parse.
	 * @return the list of authors.
	 */
	public List<Person> extractPersonsFrom(String authorText) {
		final List<Person> persons = new ArrayList<>();
		this.nameParser.parseNames(authorText, (fn, von, ln, pos) -> {
			// Build last name
			final StringBuilder firstname = new StringBuilder();
			firstname.append(fn);
			if (!Strings.isNullOrEmpty(von)) {
				firstname.append(" "); //$NON-NLS-1$
				firstname.append(von);
			}
			//
			final int id = getPersonIdByName(firstname.toString(), ln);
			Person person = null;
			if (id != 0) {
				person = getPerson(id);
			}
			if (person == null) {
				person = new Person();
				person.setFirstName(firstname.toString());
				person.setLastName(ln);
			}
			persons.add(person);
		});
		return persons;
	}

	/** Replies the duplicate person names.
	 *
	 * @return the duplicate persons.
	 */
	public List<Set<Person>> computePersonDuplicate() {
		// Each list represents a group of authors that could be duplicate
		final List<Set<Person>> matchingAuthors = new ArrayList<>();

		// Copy the list of authors into a linked list in order to have better
		// performance when going through the list for detecting duplicate
		final List<Person> authorsList = new ArrayList<>(this.personRepository.findAll());

		for (int i = 0; i < authorsList.size(); ++i) {
			final Person person = authorsList.get(i);

			final Set<Person> currentMatching = new TreeSet<>();
			currentMatching.add(person);

			final ListIterator<Person> iterator2 = authorsList.listIterator(i + 1);

			while (iterator2.hasNext()) {
				final Person otherPerson = iterator2.next();
				if (this.nameComparator.isSimilar(
						person.getFirstName(), person.getLastName(),
						otherPerson.getFirstName(), otherPerson.getLastName())) {
					currentMatching.add(otherPerson);
					// Consume the other person to avoid to be treated twice times
					iterator2.remove();
				}
			}

			if (currentMatching.size() > 1) {
				matchingAuthors.add(currentMatching);
			}
		}

		return matchingAuthors;
	}

}