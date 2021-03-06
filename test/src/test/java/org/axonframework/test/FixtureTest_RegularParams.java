/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.test;

import org.axonframework.commandhandling.model.AggregateNotFoundException;
import org.hamcrest.core.IsNull;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Allard Buijze
 * @since 0.7
 */
public class FixtureTest_RegularParams {

    private FixtureConfiguration<StandardAggregate> fixture;

    @Before
    public void setUp() {
        fixture = Fixtures.newGivenWhenThenFixture(StandardAggregate.class);
        fixture.registerAggregateFactory(new StandardAggregate.Factory());
    }

    @Test
    public void testFixture_NoEventsInStore() {
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(),
                                                                     fixture.getEventBus()))
                .given()
                .when(new TestCommand(UUID.randomUUID()))
                .expectException(AggregateNotFoundException.class);
    }

    @Test
    public void testFirstFixture() {
        ResultValidator validator = fixture
                .registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(),
                                                                      fixture.getEventBus()))
                .given(new MyEvent("aggregateId", 1))
                .when(new TestCommand("aggregateId"));
        validator.expectReturnValue(null);
        validator.expectEvents(new MyEvent("aggregateId", 2));
    }

    @Test
    public void testExpectEventsIgnoresFilteredField() {
        ResultValidator validator = fixture
                .registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(),
                                                                      fixture.getEventBus()))
                .registerFieldFilter(field -> !field.getName().equals("someBytes"))
                .given(new MyEvent("aggregateId", 1))
                .when(new TestCommand("aggregateId"));
        validator.expectReturnValue(null);
        validator.expectEvents(new MyEvent("aggregateId", 2, "ignored".getBytes()));
    }

    @Test
    public void testFixture_SetterInjection() {
        MyCommandHandler commandHandler = new MyCommandHandler();
        commandHandler.setRepository(fixture.getRepository());
        fixture.registerAnnotatedCommandHandler(commandHandler)
                .given(new MyEvent("aggregateId", 1), new MyEvent("aggregateId", 2))
                .when(new TestCommand("aggregateId"))
                .expectReturnValue(IsNull.nullValue())
                .expectEvents(new MyEvent("aggregateId", 3));
    }

    @Test
    public void testFixture_GivenAList() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1), new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        fixture
                .registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(),
                                                                      fixture.getEventBus()))
                .given(givenEvents)
                .when(new TestCommand("aggregateId"))
                .expectEvents(new MyEvent("aggregateId", 4))
                .expectVoidReturnType();
    }

    @Test
    public void testFixtureDetectsStateChangeOutsideOfHandler_ExplicitValue() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1), new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        try {
            fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(),
                                                                         fixture.getEventBus()))
                    .given(givenEvents)
                    .when(new IllegalStateChangeCommand("aggregateId", 5));
            fail("Expected AssertionError");
        } catch (AssertionError e) {
            assertTrue("Wrong message: " + e.getMessage(), e.getMessage().contains(".lastNumber\""));
            assertTrue("Wrong message: " + e.getMessage(), e.getMessage().contains("<5>"));
            assertTrue("Wrong message: " + e.getMessage(), e.getMessage().contains("<4>"));
        }
    }

    @Test
    public void testFixtureIgnoredStateChangeInFilteredField() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1), new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        fixture.registerFieldFilter(field -> !field.getName().equals("lastNumber"));
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(),
                                                                     fixture.getEventBus()))
                .given(givenEvents)
                .when(new IllegalStateChangeCommand("aggregateId", 5));
    }

    @Test
    public void testFixtureDetectsStateChangeOutsideOfHandler_NullValue() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1), new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        try {
            fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(),
                                                                         fixture.getEventBus()))
                    .given(givenEvents)
                    .when(new IllegalStateChangeCommand("aggregateId", null));
            fail("Expected AssertionError");
        } catch (AssertionError e) {
            assertTrue("Wrong message: " + e.getMessage(), e.getMessage().contains(".lastNumber\""));
            assertTrue("Wrong message: " + e.getMessage(), e.getMessage().contains("<null>"));
            assertTrue("Wrong message: " + e.getMessage(), e.getMessage().contains("<4>"));
        }
    }

    @Test
    public void testFixtureDetectsStateChangeOutsideOfHandler_Ignored() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1), new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        fixture.setReportIllegalStateChange(false);
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(),
                                                                     fixture.getEventBus()))
                .given(givenEvents)
                .when(new IllegalStateChangeCommand("aggregateId", null));
    }

    @Test
    public void testFixtureDetectsStateChangeOutsideOfHandler_AggregateGeneratesIdentifier() {
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(),
                                                                     fixture.getEventBus()))
                .given()
                .when(new CreateAggregateCommand(null));
    }

    @Test
    public void testFixtureDetectsStateChangeOutsideOfHandler_AggregateDeleted() {
        TestExecutor exec = fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(),
                                                                                         fixture.getEventBus()))
                .given(new MyEvent("aggregateId", 5));
        try {
            exec.when(new DeleteCommand("aggregateId", true));
            fail("Fixture should have failed");
        } catch (AssertionError error) {
            assertTrue("Wrong message: " + error.getMessage(), error.getMessage().contains("considered deleted"));
        }
    }

    @Test
    public void testFixture_AggregateDeleted() {
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(),
                                                                     fixture.getEventBus()))
                .given(new MyEvent("aggregateId", 5))
                .when(new DeleteCommand("aggregateId", false))
                .expectEvents(new MyAggregateDeletedEvent(false));
    }

    @Test
    public void testFixtureGivenCommands() {
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(),
                                                                     fixture.getEventBus()))
                .givenCommands(new CreateAggregateCommand("aggregateId"),
                               new TestCommand("aggregateId"),
                               new TestCommand("aggregateId"),
                               new TestCommand("aggregateId"))
                .when(new TestCommand("aggregateId"))
                .expectEvents(new MyEvent("aggregateId", 4));
    }

    @Test
    public void testFixture_CommandHandlerDispatchesNonDomainEvents() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1), new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.getRepository(),
                                                               fixture.getEventBus());
        // the domain events are part of the transaction, but the command handler directly dispatches an application
        // event to the event bus. This event dispatched anyway. The
        fixture
                .registerAnnotatedCommandHandler(commandHandler)
                .given(givenEvents)
                .when(new PublishEventCommand("aggregateId"))
                .expectEvents(new MyApplicationEvent());
    }

    @Test
    public void testFixture_ReportWrongNumberOfEvents() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1), new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.getRepository(),
                                                               fixture.getEventBus());
        try {
            fixture.registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new TestCommand("aggregateId"))
                    .expectEvents(new MyEvent("aggregateId", 4), new MyEvent("aggregateId", 5));
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains("org.axonframework.test.MyEvent <|> "));
        }
    }

    @Test
    public void testFixture_ReportWrongEvents() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1), new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.getRepository(),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new TestCommand("aggregateId"))
                    .expectEvents(new MyOtherEvent());
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains("org.axonframework.test.MyOtherEvent <|>"
                                                       + " org.axonframework.test.MyEvent"));
        }
    }

    @Test
    public void testFixture_UnexpectedException() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1), new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.getRepository(),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new StrangeCommand("aggregateId"))
                    .expectVoidReturnType();
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains("but got <exception of type [StrangeCommandReceivedException]>"));
        }
    }

    @Test
    public void testFixture_UnexpectedReturnValue() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1), new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.getRepository(),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new TestCommand("aggregateId"))
                    .expectException(RuntimeException.class);
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains("The command handler returned normally, but an exception was expected"));
            assertTrue(e.getMessage().contains(
                    "<an instance of java.lang.RuntimeException> but returned with <null>"));
        }
    }

    @Test
    public void testFixture_WrongReturnValue() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1), new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.getRepository(),
                                                               fixture.getEventBus());
        try {
            fixture.registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new TestCommand("aggregateId"))
                    .expectReturnValue("some");
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage(), e.getMessage().contains("<\"some\"> but got <null>"));
        }
    }

    @Test
    public void testFixture_WrongExceptionType() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1), new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.getRepository(),
                                                               fixture.getEventBus());
        try {
            fixture.registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new StrangeCommand("aggregateId"))
                    .expectException(IOException.class);
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains(
                    "<an instance of java.io.IOException> but got <exception of type [StrangeCommandReceivedException]>"));
        }
    }

    @Test
    public void testFixture_WrongEventContents() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1), new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.getRepository(),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new TestCommand("aggregateId"))
                    .expectEvents(new MyEvent("aggregateId", 5)) // should be 4
                    .expectVoidReturnType();
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains(
                    "In an event of type [MyEvent], the property [someValue] was not as expected."));
            assertTrue(e.getMessage().contains("Expected <5> but got <4>"));
        }
    }

    @Test
    public void testFixture_WrongEventContents_WithNullValues() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1), new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.getRepository(),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new TestCommand("aggregateId"))
                    .expectEvents(new MyEvent("aggregateId", null)) // should be 4
                    .expectVoidReturnType();
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains(
                    "In an event of type [MyEvent], the property [someValue] was not as expected."));
            assertTrue(e.getMessage().contains("Expected <<null>> but got <4>"));
        }
    }

    @Test
    public void testFixture_ExpectedPublishedSameAsStored() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1), new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.getRepository(),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new StrangeCommand("aggregateId"))
                    .expectException(StrangeCommandReceivedException.class)
                    .expectEvents(new MyEvent("aggregateId", 4)); // should be 4
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains("The published events do not match the expected events"));
            assertTrue(e.getMessage().contains("org.axonframework.test.MyEvent <|> "));
            assertTrue(e.getMessage().contains("probable cause"));
        }
    }
}
