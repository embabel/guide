Embabel Agent Framework User Guide

Rod Johnson, Alex Hein-Heifetz, Dr. Igor Dayen, Jim Clark, Arjen Poutsma, Jasper Blues

<!-- image -->

## Chapter 1. Overview

## 1.1. Why do we need an Agent Framework?

Aren't LLMs smart enough to solve our problems directly? Aren't MCP tools all we need to allow them to solve complex
problems?

But there are many reasons that a higher level orchestration technology is needed, especially for business applications.
Here are some of the most important:

- Explainability : Why were choices made in solving a problem?
- Discoverability :  How do we find the right tools at each point, and ensure that models aren't confused in choosing
  between them?
- Ability to mix models , so that we are not reliant on God models but can use local, cheaper, private models for many
  tasks
- Ability to inject guardrails at any point in a flow
- Ability to manage flow execution and introduce greater resilience
- Composability of flows at scale . We'll soon be seeing not just agents running on one system, but federations of
  agents.
- Safer integration with sensitive existing systems such as databases, where it is dangerous to allow even the best LLM
  write access.

Agent frameworks break complex tasks into smaller, manageable components, offering greater control and predictability.

Agent frameworks offer "code agency" as well as "LLM agency." This division is well described in this paper from NVIDIA
Research.

Further reading:

- Embabel: A new Agent Platform For the JVM
- The Embabel Vision

## 1.2. Embabel Differentiators

## 1.2.1. Sophisticated Planning

Goes beyond a finite state machine or sequential execution with nesting by introducing a true planning step, using a
non-LLM AI algorithm. This enables the system to perform tasks it wasn't programmed to do by combining known steps in a
novel order, as well as make decisions about parallelization and other runtime behavior.

## 1.2.2. Superior Extensibility and Reuse

Because of dynamic planning, adding more domain objects, actions, goals and conditions can

extend the capability of the system, without editing FSM definitions or existing code.

## 1.2.3. Strong Typing and Object Orientation

Actions, goals and conditions are informed by a domain model, which can include behavior. Everything is strongly typed
and prompts and manually authored code interact cleanly. No more magic maps. Enjoy full refactoring support.

## 1.2.4. Platform Abstraction

Clean separation between programming model and platform internals allows running locally while potentially offering
higher QoS in production without changing application code.

## 1.2.5. LLM Mixing

It is easy to build applications that mix LLMs, ensuring the most cost-effective yet capable solution. This enables the
system to leverage the strengths of different models for different tasks. In particular, it facilitates the use of local
models for point tasks. This can be important for cost and privacy.

## 1.2.6. Spring and JVM Integration

Built on Spring and the JVM, making it easy to access existing enterprise functionality and capabilities. For example:

- Spring can inject and manage agents, including using Spring AOP to decorate functions.
- Robust persistence and transaction management solutions are available.

## 1.2.7. Designed for Testability

Both unit testing and agent end-to-end testing are easy from the ground up.

## 1.3. Core Concepts

Agent frameworks break up tasks into separate smaller interactions, making LLM use more predictable and focused.

Embabel models agentic flows in terms of:

- Actions : Steps an agent takes. These are the building blocks of agent behavior.
- Goals : What an agent is trying to achieve.
- Conditions :  Conditions to while planning. Conditions are reassessed after each action is executed.
- Domain Model : Objects underpinning the flow and informing Actions, Goals and Conditions.

This enables Embabel to create a plan :  A sequence of actions to achieve a goal. Plans are dynamically formulated by
the system, not the programmer. The system replans after the

completion of each action, allowing it to adapt to new information as well as observe the effects of the previous
action. This is effectively an OODA loop.

<!-- image -->

Application developers don't usually have to deal with these concepts directly, as most conditions result from data flow
defined in code, allowing the system to infer pre and post conditions.

## 1.3.1. Complete Example

Let's look at a complete example that demonstrates how Embabel infers conditions from input/output types and manages
data flow between actions. This example comes from the Embabel Agent Examples repository:

```
@Agent(description = "Find news based on a person's star sign") ① public class StarNewsFinder { private final HoroscopeService horoscopeService; ② private final int storyCount; public StarNewsFinder ( HoroscopeService horoscopeService, ③ @Value("${star-news-finder.story.count:5}") int storyCount) { this .horoscopeService = horoscopeService; this .storyCount = storyCount; } @Action ④ public StarPerson extractStarPerson ( UserInput userInput, OperationContext context) { ⑤ return context.ai() .withLlm( OpenAiModels .GPT_41) .createObject(""" Create a person from this user input, extracting their name and star sign: %s""".formatted(userInput.getContent()), StarPerson .class); ⑥ } @Action ⑦ public Horoscope retrieveHoroscope ( StarPerson starPerson) { ⑧ // Uses regular injected Spring service - not LLM return new Horoscope (horoscopeService.dailyHoroscope(starPerson.sign())); ⑨ } @Action(toolGroups = { CoreToolGroups .WEB}) ⑩ public RelevantNewsStories findNewsStories ( StarPerson person, Horoscope horoscope, OperationContext context) { ⑪ var prompt = """ %s is an astrology believer with the sign %s. Their horoscope for today is: %s
```

```
Given this, use web tools to find %d relevant news stories. """.formatted(person.name(), person.sign(), horoscope.summary(), storyCount); return context.ai().withDefaultLlm().createObject(prompt, RelevantNewsStories .class); ⑫ } @AchievesGoal(description = "Write an amusing writeup based on horoscope and news") ⑬ @Action public Writeup writeup ( StarPerson person, RelevantNewsStories stories, Horoscope horoscope, OperationContext context) { ⑭ var llm = LlmOptions .fromCriteria( ModelSelectionCriteria .getAuto()) .withTemperature( 0.9 ); ⑮ var prompt = """ Write something amusing for %s based on their horoscope and these news stories. Format as Markdown with links. """.formatted(person.name()); return context.ai().withLlm(llm).createObject(prompt, Writeup .class); ⑯ } }
```

- ① Agent Declaration : The @Agent annotation defines this as an agent capable of a multi-step flow.
- ② Spring Integration :  Regular Spring dependency injection - the agent uses both LLM services and traditional
  business services.
- ③ Service Injection : HoroscopeService is injected like any Spring bean - agents can mix AI and nonAI operations
  seamlessly.
- ④ Action Definition : @Action marks methods as steps the agent can take. Each action represents a capability.
- ⑤ Input Condition Inference : The method signature extractStarPerson(UserInput userInput, …) tells Embabel:
- Precondition : "A UserInput object must be available"
- Required Data : The agent needs user input to proceed
- Capability : This action can extract structured data from unstructured input
- ⑥ Output Condition Creation : Returning StarPerson creates:
- Postcondition : "A StarPerson object is now available in the world state"
- Data Availability : This output becomes input for subsequent actions
- Type Safety : The domain model enforces structure
- ⑦ Non-LLM Action : Not all actions use LLMs this demonstrates hybrid AI/traditional programming.

- ⑧ Data Flow Chain : The method signature retrieveHoroscope(StarPerson starPerson) creates:
- Precondition : "A StarPerson object must exist" (from previous action)
- Dependency : This action can only execute after extractStarPerson completes
- Service Integration : Uses the injected horoscopeService rather than an LLM
- ⑨ Regular Service Call : This action calls a traditional Spring service - demonstrating how agents blend AI and
  conventional operations.
- ⑩ Tool Requirements : toolGroups = {CoreToolGroups.WEB} specifies this action needs web search capabilities.
- ⑪ Multi-Input Dependencies :  This method requires both StarPerson and Horoscope - showing complex data flow
  orchestration.
- ⑫ Tool-Enabled LLM : The LLM can use web tools to search for current news stories based on the horoscope context.
- ⑬ Goal Achievement : @AchievesGoal marks this as a terminal action that completes the agent's objective.
- ⑭ Complex Input Requirements :  The final action requires three different data types, showing sophisticated
  orchestration.
- ⑮ Creative Configuration :  High temperature  (0.9)  optimizes for creative, entertaining output appropriate for
  amusing writeups.
- ⑯ Final Output : Returns Writeup , completing the agent's goal with personalized content.

State is managed by the framework, through the process blackboard

## 1.3.2. The Inferred Execution Plan

Based on the type signatures alone, Embabel automatically infers this execution plan:

Goal : Produce a Writeup (final return type of @AchievesGoal action)

The initial plan:

- To emit Writeup → need writeup() action
- writeup() requires StarPerson , RelevantNewsStories , and Horoscope
- To get StarPerson → need extractStarPerson() action
- To get Horoscope → need retrieveHoroscope() action (requires StarPerson )
- To get RelevantNewsStories → need findNewsStories() action (requires StarPerson and Horoscope )
- extractStarPerson() requires UserInput → must be provided by user

Execution sequence:

```
UserInput → extractStarPerson() → StarPerson → retrieveHoroscope() → Horoscope → findNewsStories() → RelevantNewsStories → writeup() → Writeup and achieves goal.
```

## 1.3.3. Key Benefits of Type-Driven Flow

Automatic Orchestration :  No manual workflow definition needed - the agent figures out the sequence from type
dependencies. This is particularly beneficial if things go wrong, as the planner can re-evaluate the situation and may
be able to find an alternative path to the goal.

Dynamic Replanning :  After each action, the agent reassesses what's possible based on available data objects.

Type Safety :  Compile-time guarantees that data flows correctly between actions. No magic string keys.

Flexible Execution :  If multiple actions could produce the required input type, the agent chooses based on context and
efficiency. (Actions can have cost and value.)

This demonstrates how Embabel transforms simple method signatures into sophisticated multi-step agent behavior, with the
complex orchestration handled automatically by the framework.

## Chapter 2. Getting Started

## 2.1. Quickstart

There are two GitHub template repos you can use to create your own project:

- Java template - github.com/embabel/java-agent-template
- Kotlin template - github.com/embabel/kotlin-agent-template

Or you can use our project creator to create a custom project:

```
uvx --from git+https://github.com/embabel/project-creator.git project-creator
```

## 2.2. Getting the Binaries

The easiest way to get started with Embabel Agent is to add the Spring Boot starter dependency to your project.

## 2.2.1. Maven

Add the Embabel Agent Spring Boot starter to your pom.xml :

```
<dependency> <groupId> com.embabel.agent </groupId> <artifactId> embabel-agent-starter </artifactId> <version> ${embabel-agent.version} </version> </dependency>
```

You'll also need to add the Embabel repository to your pom.xml :

```
<repositories> <repository> <id> embabel-releases </id> <url> https://repo.embabel.com/artifactory/libs-release </url> <releases> <enabled> true </enabled> </releases> <snapshots> <enabled> false </enabled> </snapshots> </repository> <repository> <id> embabel-snapshots </id> <url> https://repo.embabel.com/artifactory/libs-snapshot </url> <releases>
```

```
<enabled> false </enabled> </releases> <snapshots> <enabled> true </enabled> </snapshots> </repository> <repository> <id> spring-milestones </id> <url> https://repo.spring.io/milestone </url> <snapshots> <enabled> false </enabled> </snapshots> </repository> </repositories>
```

## 2.2.2. Gradle

Add the required repositories to your build.gradle.kts :

```
repositories { mavenCentral () maven { name = "embabel-releases" url = uri ("https://repo.embabel.com/artifactory/libs-release") mavenContent { releasesOnly () } } maven { name = "embabel-snapshots" url = uri ("https://repo.embabel.com/artifactory/libs-snapshot") mavenContent { snapshotsOnly () } } maven { name = "Spring Milestones" url = uri ("https://repo.spring.io/milestone") } }
```

Add the Embabel Agent starter dependency:

```
dependencies { implementation ("com.embabel.agent:embabel-agent-starter:${embabel-agent.version}") }
```

For Gradle Groovy DSL ( build.gradle ):

```
repositories { mavenCentral() maven { name = 'embabel-releases' url = 'https://repo.embabel.com/artifactory/libs-release' mavenContent { releasesOnly() } } maven { name = 'embabel-snapshots' url = 'https://repo.embabel.com/artifactory/libs-snapshot' mavenContent { snapshotsOnly() } } maven { name = 'Spring Milestones' url = 'https://repo.spring.io/milestone' } } dependencies { implementation 'com.embabel.agent:embabel-agent-starter:${embabel-agent.version}' }
```

## 2.2.3. Environment Setup

Before running your application, you'll need to set up your environment with API keys for the LLM providers you plan to
use. Required: : For OpenAI models (GPT-4, GPT-5, etc.)

```
Optional but recommended: -ANTHROPIC_API_KEY Example .env file:
```

```
OPENAI_API_KEY : For Anthropic models (Claude 3.x, etc.)
```

```
OPENAI_API_KEY=your_openai_api_key_here ANTHROPIC_API_KEY=your_anthropic_api_key_here
```

## 2.3. Getting Embabel Running

## 2.3.1. Running the Examples

The quickest way to get started with Embabel is to run the examples:

```
# Clone and run examples
```

```
git clone https://github.com/embabel/embabel-agent-examples cd embabel-agent-examples/scripts/java ./shell.sh
```

<!-- image -->

Choose the java or kotlin scripts directory depending on your preference.

## 2.3.2. Prerequisites

- Java 21+
- API Key from OpenAI or Anthropic
- Maven 3.9+ (optional)

Set your API keys:

```
export OPENAI_API_KEY="your_openai_key" export ANTHROPIC_API_KEY="your_anthropic_key"
```

## 2.3.3. Using the Shell

Spring Shell is an easy way to interact with the Embabel agent framework, especially during development.

Type help to see available commands. Use execute or x to run an agent:

```
execute "Lynda is a Scorpio, find news for her" -p -r
```

This will look for an agent, choose the star finder agent and run the flow. -p will log prompts -r will log LLM
responses. Omit these for less verbose logging.

Options:

- -p logs prompts
- -r logs LLM responses

Use the chat command to enter an interactive chat with the agent. It will attempt to run the most appropriate agent for
each command.

<!-- image -->

Spring Shell supports history. Type !! to repeat the last command. This will survive restarts, so is handy when
iterating on an agent.

## 2.3.4. Example Commands

Try these commands in the shell:

```
# Simple horoscope agent
```

```
execute "My name is Sarah and I'm a Leo" # Research with web tools (requires Docker Desktop with MCP extension) execute "research the recent australian federal election. what is the position of the Greens party?" # Fact checking x "fact check the following: holden cars are still made in australia"
```

## 2.3.5. Implementing Your Own Shell Commands

Particularly during development, you may want to implement your own shell commands to try agents or flows. Simply write
a Spring Shell component and Spring will inject it and register it automatically.

For example, you can inject the AgentPlatform and use it to invoke agents directly, as in this code from the examples
repository:

```
@ShellComponent public record SupportAgentShellCommands ( AgentPlatform agentPlatform ) { @ShellMethod("Get bank support for a customer query") public String bankSupport ( @ShellOption(value = "id", help = "customer id", defaultValue = "123") Long id, @ShellOption(value = "query", help = "customer query", defaultValue = "What's my balance, including pending amounts?") String query ) { var supportInput = new SupportInput (id, query); System .out.println("Support input: " + supportInput); var invocation = AgentInvocation .builder(agentPlatform) .options( ProcessOptions .builder().verbosity(v -> v.showPrompts( true )).build()) .build( SupportOutput .class); var result = invocation.invoke(supportInput); return result.toString(); } }
```

## 2.4. Adding a Little AI to Your Application

Before we get into the magic of full-blown Embabel agents, let's see how easy it is to add a little AI to your
application using the Embabel framework. Sometimes this is all you need.

The simplest way to use Embabel is to inject an OperationContext and use its AI capabilities directly.

This approach is consistent with standard Spring dependency injection patterns.

```
package com.embabel.example.injection ; import com.embabel.agent.api.common.OperationContext ; import com.embabel.common.ai.model.LlmOptions ; import org.springframework.stereotype.Component ; /** * Demonstrate the simplest use of Embabel's AI capabilities, * injecting an AI helper into a Spring component. * The jokes will be terrible, but don't blame Embabel, blame the LLM. */ @Component public record InjectedComponent ( Ai ai) { public record Joke ( String leadup, String punchline) { } public String tellJokeAbout ( String topic) { return ai .withDefaultLlm() .generateText("Tell me a joke about " + topic); } public Joke createJokeObjectAbout ( String topic1, String topic2, String voice) { return ai .withLlm( LlmOptions .withDefaultLlm().withTemperature(. 8 )) .createObject(""" Tell me a joke about %s and %s. The voice of the joke should be %s. The joke should have a leadup and a punchline. """.formatted(topic1, topic2, voice), Joke .class); } }
```

This example demonstrates several key aspects of Embabel's design philosophy:

- Standard Spring Integration : The Ai object is injected like any other Spring dependency using constructor injection
- Simple API :  Access AI capabilities through the Ai interface directly or OperationContext.ai() , which can also be
  injected in the same way
- Flexible Configuration : Configure LLM options like temperature on a per-call basis
- Type Safety : Generate structured objects directly with createObject() method
- Consistent Patterns : Works exactly like you'd expect any Spring component to work

The Ai type provides access to all of Embabel's AI capabilities without requiring a full agent setup, making it perfect
for adding AI features to existing applications incrementally.

<!-- image -->

The Ai and OperationContext` APIs are used throughout Embabel applications, as a convenient gateway to key AI and other
functionality.

## 2.5. Writing Your First Agent

The easiest way to create your first agent is to use the Java or Kotlin template repositories.

## 2.5.1. Using the Template

Create a new project from the Java template or Kotlin template by clicking "Use this template" on GitHub.

Or use the project creator:

```
uvx --from git+https://github.com/embabel/project-creator.git project-creator
```

## 2.5.2. Example: WriteAndReviewAgent

The Java template includes a WriteAndReviewAgent that demonstrates key concepts:

```
@Agent(description = "Agent that writes and reviews stories") public class WriteAndReviewAgent { @Action public Story writeStory ( UserInput userInput, OperationContext context) { // High temperature for creativity var writer = LlmOptions .withModel( OpenAiModels .GPT_4O_MINI) .withTemperature( 0.8 ) .withPersona("You are a creative storyteller"); return context.ai() .withLlm(writer) .createObject("Write a story about: " + userInput.getContent(), Story .class); } @AchievesGoal(description = "Review and improve the story") @Action public ReviewedStory reviewStory ( Story story, OperationContext context) { // Low temperature for analytical review var reviewer = LlmOptions .withModel( OpenAiModels .GPT_4O_MINI) .withTemperature( 0.2 ) .withPersona("You are a careful editor and reviewer"); String prompt = "Review this story and suggest improvements: " + story.text();
```

```
return context.ai() .withLlm(reviewer) .createObject(prompt, ReviewedStory .class); } }
```

## 2.5.3. Key Concepts Demonstrated

## Multiple LLMs with Different Configurations:

- Writer LLM uses high temperature (0.8) for creativity
- Reviewer LLM uses low temperature (0.2) for analytical review
- Different personas guide the model behavior

## Actions and Goals:

- @Action methods are the steps the agent can take
- @AchievesGoal marks the final action that completes the agent's work

## Domain Objects:

- Story and ReviewedStory are strongly-typed domain objects
- Help structure the interaction between actions

## 2.5.4. Running Your Agent

Set your API keys and run the shell:

```
export OPENAI_API_KEY="your_key_here" ./scripts/shell.sh
```

In the shell, try:

```
x "Tell me a story about a robot learning to paint"
```

The agent will:

1. Generate a creative story using the writer LLM
2. Review and improve it using the reviewer LLM
3. Return the final reviewed story

## 2.5.5. Next Steps

- Explore the examples repository for more complex agents

- Read the Reference Documentation for detailed API information
- Try building your own domain-specific agents

## Chapter 3. Reference

## 3.1. Invoking an Agent

Agents can be invoked programmatically or via user input.

See Invoking Embabel Agents for details on programmatic invocation. Programmatic invocation typically involves
structured types other than user input.

In the case of user input, an LLM will choose the appropriate agent via the Autonomy class. Behavior varies depending on
configuration:

- In closed mode, the LLM will select the agent based on the user input and the available agents in the system.
- In open mode, the LLM will select the goal based on the user input and then assemble an agent that can achieve that
  goal from the present world state.

## 3.2. Agent Process Flow

When an agent is invoked, Embabel creates an AgentProcess with a unique identifier that manages the complete execution
lifecycle.

## 3.2.1. AgentProcess Lifecycle

An AgentProcess maintains state throughout its execution and can transition between various states:

## Process States:

- NOT\_STARTED : The process has not started yet
- RUNNING : The process is executing without any known problems
- COMPLETED : The process has completed successfully
- FAILED : The process has failed and cannot continue
- TERMINATED : The process was killed by an early termination policy
- KILLED : The process was killed by the user or platform
- STUCK : The process cannot formulate a plan to progress (may be temporary)
- WAITING : The process is waiting for user input or external event
- PAUSED : The process has paused due to scheduling policy

## Process Execution Methods:

- tick() : Perform the next single step and return when an action completes
- run() : Execute the process as far as possible until completion, failure, or a waiting state

These methods are not directly called by user code, but are managed by the framework to control execution flow.

Each AgentProcess maintains:

- Unique ID : Persistent identifier for tracking and reference
- History : Record of all executed actions with timing information
- Goal : The objective the process is trying to achieve
- Failure Info : Details about any failure that occurred
- Parent ID : Reference to parent process for nested executions

## 3.2.2. Planning

Planning occurs after each action execution using Goal-Oriented Action Planning  (GOAP). The planning process:

1. Analyze Current State : Examine the current blackboard contents and world state
2. Identify Available Actions : Find all actions that can be executed based on their preconditions
3. Search for Action Sequences : Use A* algorithm to find optimal paths to achieve the goal
4. Select Optimal Plan : Choose the best action sequence based on cost and success probability
5. Execute Next Action : Run the first action in the plan and replan

This creates a dynamic OODA loop (Observe-Orient-Decide-Act):  Observe :  Check current blackboard state and action
results Orient : Understand what has changed since the last planning cycle Decide :  Formulate or update the plan based
on new information Act :  Execute the next planned action

The replanning approach allows agents to:

- Adapt to unexpected action results
- Handle dynamic environments where conditions change
- Recover from partial failures
- Take advantage of new opportunities that arise

## 3.2.3. Blackboard

The Blackboard serves as the shared memory system that maintains state throughout the agent process execution. It
implements the Blackboard architectural pattern, a knowledge-based system approach.

Most of the time, user code doesn't need to interact with the blackboard directly, as it is managed by the framework.
For example, action inputs come from the blackboard, and action outputs are automatically added to the blackboard, and
conditions are evaluated based on its contents.

## Key Characteristics:

- Central Repository : Stores all domain objects, intermediate results, and process state
- Type-Based Access : Objects are indexed and retrieved by their types
- Ordered Storage : Objects maintain the order they were added, with latest being default
- Immutable Objects : Once added, objects cannot be modified (new versions can be added)
- Condition Tracking : Maintains boolean conditions used by the planning system

## Core Operations:

```
// Add objects to blackboard blackboard += person blackboard["result"] = analysis // Retrieve objects by type val person = blackboard.last< Person >() val allPersons = blackboard.all< Person >() // Check conditions blackboard. setCondition ("userVerified", true ) val verified = blackboard. getCondition ("userVerified")
```

## Data Flow:

1. Input Processing : Initial user input is added to the blackboard
2. Action Execution : Each action reads inputs from blackboard and adds results
3. State Evolution : Blackboard accumulates objects representing the evolving state
4. Planning Input : Current blackboard state informs the next planning cycle
5. Result Extraction : Final results are retrieved from blackboard upon completion

## The blackboard enables:

- Loose Coupling : Actions don't need direct references to each other
- Flexible Data Flow : Actions can consume any available data of the right type
- State Persistence : Complete execution history is maintained
- Debugging Support : Full visibility into state evolution for troubleshooting

## 3.3. Goals, Actions and Conditions

## 3.4. Domain Objects

Domain objects in Embabel are not just strongly-typed data structures - they are real objects with behavior that can be
selectively exposed to LLMs and used in agent actions.

## 3.4.1. Objects with Behavior

Unlike simple structs or DTOs, Embabel domain objects can encapsulate business logic and expose it to LLMs through the
@Tool annotation:

```
@Entity public class Customer { private String name; private LoyaltyLevel loyaltyLevel; private List < Order > orders; @Tool(description = "Calculate the customer's loyalty discount percentage") public BigDecimal getLoyaltyDiscount () { return loyaltyLevel.calculateDiscount(orders.size()); } @Tool(description = "Check if customer is eligible for premium service") public boolean isPremiumEligible () { return orders.stream() .mapToDouble(Order::getTotal) .sum() > 1000.0 ; } // Regular methods not exposed to LLMs private void updateLoyaltyLevel () { // Internal business logic } }
```

## 3.4.2. Selective Tool Exposure

The @Tool annotation allows you to selectively expose domain object methods to LLMs:

- Business Logic : Expose methods that provide business value to the LLM
- Calculated Properties : Methods that compute derived values
- Business Rules : Methods that implement domain-specific rules
- Keep Private : Internal implementation details remain hidden

## 3.4.3. Use in Actions

Domain objects can be used naturally in action methods, combining LLM interactions with traditional object-oriented
programming:

```
@Action public Recommendation generateRecommendation ( Customer customer, OperationContext context) { // LLM has access to customer.getLoyaltyDiscount() and
```

```
customer.isPremiumEligible() // as tools, plus the customer object structure String prompt = String .format( "Generate a personalized recommendation for %s based on their profile", customer.getName() ); return context.ai() .withDefaultLlm() .createObject(prompt, Recommendation .class); }
```

## 3.4.4. Domain Understanding is Critical

As outlined in Rod Johnson's blog introducing DICE  (Domain-Integrated Context Engineering)  in Context Engineering
Needs Domain Understanding, domain understanding is fundamental to effective context engineering. Domain objects serve
as the bridge between:

- Business Domain : Real-world entities and their relationships
- Agent Behavior : How LLMs understand and interact with the domain
- Code Actions : Traditional programming logic that operates on domain objects

## 3.4.5. Benefits

- Rich Context : LLMs receive both data structure and behavioral context
- Encapsulation : Business logic stays within domain objects where it belongs
- Reusability : Domain objects can be used across multiple agents
- Testability : Domain logic can be unit tested independently
- Evolution : Adding new tools to domain objects extends agent capabilities

This approach ensures that agents work with meaningful business entities rather than generic data structures, leading to
more natural and effective AI interactions.

## 3.5. Configuration

## 3.5.1. Enabling Embabel

Annotate your Spring Boot application class to get agentic behavior.

Example:

```
@SpringBootApplication @EnableAgentShell @EnableAgents( loggingTheme = LoggingThemes .STAR_WARS,
```

```
localModels = { "docker" }, mcpClients = { "docker" } ) class MyAgentApplication { public static void main ( String [] args) { SpringApplication .run( MyAgentApplication .class, args); } }
```

This is a normal Spring Boot application class. You can add other Spring Boot annotations as needed. @EnableAgentShell
enables the agent shell, which allows you to interact with agents via a command line interface. This is optional.

@EnableAgents enables the agent framework. It allows you to specify a logging theme (optional) and well-known sources of
local models and MCP tools. In this case we're using Docker as a source of local models and MCP tools.

## 3.5.2. Configuration Properties

The following table lists all available configuration properties in Embabel Agent Platform. Properties are organized by
their configuration prefix and include default values where applicable. They can be set via application.properties ,
application.yml , profile-specific configuration files or environment variables, as per standard Spring configuration
practices.

## Platform Configuration

From AgentPlatformProperties - unified configuration for all agent platform properties.

| Property                            | Type   | Default                        | Description                 |
|-------------------------------------|--------|--------------------------------|-----------------------------|
| embabel.agent.platform.name         | String | embabel- default               | Core platform identity name |
| embabel.agent.platform.desc ription | String | Embabel Default Agent Platform | Platform description        |

## Agent Scanning

From AgentPlatformProperties.ScanningConfig - configures scanning of the classpath for agents.

| Property                                    | Type    | Default | Description                                                        |
|---------------------------------------------|---------|---------|--------------------------------------------------------------------|
| embabel.agent.platform.scan ning.annotation | Boolean | true    | Whether to auto register beans with @Agent and @Agentic annotation |
| embabel.agent.platform.scan ning.bean       | Boolean | false   | Whether to auto register as agents Spring beans of type  Agent     |

## Ranking Configuration

From AgentPlatformProperties.RankingConfig - configures ranking of agents and goals based on user input when the
platform should choose the agent or goal.

| Property                                             | Type   | Default | Description                                                       |
|------------------------------------------------------|--------|---------|-------------------------------------------------------------------|
| embabel.agent.platform.rank ing.llm                  | String | null    | Name of the LLM to use for ranking, or null to use auto selection |
| embabel.agent.platform.rank ing.max-attempts         | Int    | 5       | Maximum number of attempts to retry ranking                       |
| embabel.agent.platform.rank ing.backoff-millis       | Long   | 100     | Initial backoff time in milliseconds                              |
| embabel.agent.platform.rank ing.backoff-multiplier   | Double | 5.0     | Multiplier for backoff time                                       |
| embabel.agent.platform.rank ing.backoff-max-interval | Long   | 180000  | Maximum backoff time in milliseconds                              |

## LLM Operations

From AgentPlatformProperties.LlmOperationsConfig - configuration for LLM operations including prompts and data binding.

| Property                                                                     | Type    | Default                      | Description                                                                     |
|------------------------------------------------------------------------------|---------|------------------------------|---------------------------------------------------------------------------------|
| embabel.agent.platform.llm- operations.prompts.maybe- prompt-template        | String  | maybe_pr ompt_con tributio n | Template for "maybe" prompt, enabling failure result when LLM lacks information |
| embabel.agent.platform.llm- operations.prompts.generate -examples-by-default | Boolean | true                         | Whether to generate examples by default                                         |
| embabel.agent.platform.llm- operations.data- binding.max-attempts            | Int     | 10                           | Maximum retry attempts for data binding                                         |
| embabel.agent.platform.llm- operations.data- binding.fixed-backoff- millis   | Long    | 30                           | Fixed backoff time in milliseconds between retries                              |

## Process ID Generation

From AgentPlatformProperties.ProcessIdGenerationConfig - configuration for process ID generation.

| Property                                                       | Type    | Default | Description                                         |
|----------------------------------------------------------------|---------|---------|-----------------------------------------------------|
| embabel.agent.platform.proc ess-id-generation.include- version | Boolean | false   | Whether to include version in process ID generation |

| Property                                                          | Type    | Default | Description                                            |
|-------------------------------------------------------------------|---------|---------|--------------------------------------------------------|
| embabel.agent.platform.proc ess-id-generation.include- agent-name | Boolean | false   | Whether to include agent name in process ID generation |

## Autonomy Configuration

From AgentPlatformProperties.AutonomyConfig - configures thresholds for agent and goal selection. Certainty below
thresholds will result in failure to choose an agent or goal.

| Property                                                   | Type   | Default | Description                               |
|------------------------------------------------------------|--------|---------|-------------------------------------------|
| embabel.agent.platform.auto nomy.agent-confidence-cut- off | Double | 0.6     | Confidence threshold for agent operations |
| embabel.agent.platform.auto nomy.goal-confidence-cut- off  | Double | 0.6     | Confidence threshold for goal achievement |

## Model Provider Configuration

From AgentPlatformProperties.ModelsConfig - model provider integration configurations.

## Anthropic

| Property                                                       | Type   | Default | Description                              |
|----------------------------------------------------------------|--------|---------|------------------------------------------|
| embabel.agent.platform.mode ls.anthropic.max-attempts          | Int    | 10      | Maximum retry attempts                   |
| embabel.agent.platform.mode ls.anthropic.backoff-millis        | Long   | 5000    | Initial backoff time in milliseconds     |
| embabel.agent.platform.mode ls.anthropic.backoff- multiplier   | Double | 5       | Backoff multiplier                       |
| embabel.agent.platform.mode ls.anthropic.backoff-max- interval | Long   | 180000  | Maximum backoff interval in milliseconds |

## OpenAI

| Property                                                    | Type   | Default | Description                              |
|-------------------------------------------------------------|--------|---------|------------------------------------------|
| embabel.agent.platform.mode ls.openai.max-attempts          | Int    | 10      | Maximum retry attempts                   |
| embabel.agent.platform.mode ls.openai.backoff-millis        | Long   | 5000    | Initial backoff time in milliseconds     |
| embabel.agent.platform.mode ls.openai.backoff- multiplier   | Double | 5       | Backoff multiplier                       |
| embabel.agent.platform.mode ls.openai.backoff-max- interval | Long   | 180000  | Maximum backoff interval in milliseconds |

## Server-Sent Events

From AgentPlatformProperties.SseConfig - server-sent events configuration.

| Property                                        | Type | Default | Description                       |
|-------------------------------------------------|------|---------|-----------------------------------|
| embabel.agent.platform.sse. max-buffer-size     | Int  | 100     | Maximum buffer size for SSE       |
| embabel.agent.platform.sse. max-process-buffers | Int  | 1000    | Maximum number of process buffers |

## Test Configuration

From AgentPlatformProperties.TestConfig - test configuration.

| Property                               | Type    | Default | Description                             |
|----------------------------------------|---------|---------|-----------------------------------------|
| embabel.agent.platform.test .mock-mode | Boolean | true    | Whether to enable mock mode for testing |

## Process Repository Configuration

From ProcessRepositoryProperties - configuration for the agent process repository.

| Property                                               | Type | Default | Description                                                                                                                                          |
|--------------------------------------------------------|------|---------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| embabel.agent.platform.proc ess-repository.window-size | Int  | 1000    | Maximum number of agent processes to keep in memory when using default InMemoryAgentProcessRepository . When exceeded, oldest processes are evicted. |

## Standalone LLM Configuration

## LLM Operations Prompts

From LlmOperationsPromptsProperties - properties for ChatClientLlmOperations operations.

| Property                                                      | Type     | Default                      | Description                                                                                                                                                  |
|---------------------------------------------------------------|----------|------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| embabel.llm- operations.prompts.maybe- prompt-template        | String   | maybe_pr ompt_con tributio n | Template to use for the "maybe" prompt, which can enable a failure result if the LLM does not have enough information to create the desired output structure |
| embabel.llm- operations.prompts.generate -examples-by-default | Boolean  | true                         | Whether to generate examples by default                                                                                                                      |
| embabel.llm- operations.prompts.default- timeout              | Duration | 60s                          | Default timeout for operations                                                                                                                               |

## LLM Data Binding

From LlmDataBindingProperties - data binding properties with retry configuration for LLM operations.

| Property                                                    | Type | Default | Description                                        |
|-------------------------------------------------------------|------|---------|----------------------------------------------------|
| embabel.llm- operations.data- binding.max-attempts          | Int  | 10      | Maximum retry attempts for data binding            |
| embabel.llm- operations.data- binding.fixed-backoff- millis | Long | 30      | Fixed backoff time in milliseconds between retries |

## Additional Model Providers

## AWS Bedrock

From BedrockProperties - AWS Bedrock model configuration properties.

| Property                                          | Type   | Default | Description                         |
|---------------------------------------------------|--------|---------|-------------------------------------|
| embabel.models.bedrock.mode ls                    | List   | []      | List of Bedrock models to configure |
| embabel.models.bedrock.mode ls[].name             | String | ""      | Model name                          |
| embabel.models.bedrock.mode ls[].knowledge-cutoff | String | ""      | Knowledge cutoff date               |
| embabel.models.bedrock.mode ls[].input-price      | Double | 0.0     | Input token price                   |
| embabel.models.bedrock.mode ls[].output-price     | Double | 0.0     | Output token price                  |

## Docker Local Models

From DockerProperties - configuration for Docker local models (OpenAI-compatible).

| Property                                    | Type   | Default                   | Description                              |
|---------------------------------------------|--------|---------------------------|------------------------------------------|
| embabel.docker.models.base- url             | String | localhos t:12434/ engines | Base URL for Docker model endpoint       |
| embabel.docker.models.max- attempts         | Int    | 10                        | Maximum retry attempts                   |
| embabel.docker.models.backo ff-millis       | Long   | 2000                      | Initial backoff time in milliseconds     |
| embabel.docker.models.backo ff-multiplier   | Double | 5.0                       | Backoff multiplier                       |
| embabel.docker.models.backo ff-max-interval | Long   | 180000                    | Maximum backoff interval in milliseconds |

## Migration Support

From DeprecatedPropertyScanningConfig and DeprecatedPropertyWarningConfig - configuration for migrating from older
versions of Embabel.

<!-- image -->

These properties will be removed before Embabel 1.0.0 release.

| Property                                                              | Type         | Default                                               | Description                                                                                 |
|-----------------------------------------------------------------------|--------------|-------------------------------------------------------|---------------------------------------------------------------------------------------------|
| embabel.agent.platform.migr ation.scanning.enabled                    | Boolean      | false                                                 | Whether deprecated property scanning is enabled (disabled by default for production safety) |
| embabel.agent.platform.migr ation.scanning.include- packages          | List<String> | ["com.em babel.ag ent", "com.emb abel.age nt.shell "] | Base packages to scan for deprecated conditional annotations                                |
| embabel.agent.platform.migr ation.scanning.exclude- packages          | List<String> | Extensiv e default list                               | Package prefixes to exclude from scanning                                                   |
| embabel.agent.platform.migr ation.scanning.additional- excludes       | List<String> | []                                                    | Additional user-specific packages to exclude                                                |
| embabel.agent.platform.migr ation.scanning.auto- exclude-jar-packages | Boolean      | false                                                 | Whether to automatically exclude JAR- based packages using classpath detection              |
| embabel.agent.platform.migr ation.scanning.max-scan- depth            | Int          | 10                                                    | Maximum depth for package scanning                                                          |
| embabel.agent.platform.migr ation.warnings.individual- logging        | Boolean      | true                                                  | Whether to enable individual warning logging. When false, only aggregated summary is logged |

## 3.6. Annotation model

Embabel provides a Spring-style annotation model to define agents, actions, goals, and conditions. This is the
recommended model to use in Java, and remains compelling in Kotlin.

## 3.6.1. The @Agent annotation

This is used on a class to define an agent. It is a Spring stereotype annotation, so it triggers Spring component
scanning. Your agent class will automatically be registered as a Spring bean. It will also be registered with the agent
framework, so it can be used in agent processes.

You must provide the description parameter, which is a human-readable description of the agent. This is particularly
important as it may be used by the LLM in agent selection.

## 3.6.2. The @Action annotation

The @Action annotation is used to mark methods that perform actions within an agent.

Action metadata can be specified on the annotation, including:

- description : A human-readable description of the action.
- pre : A list of preconditions additional to the input types that must be satisfied before the action can be executed.
- post : A list of postconditions additional to the output type(s) that may be satisfied after the action is executed.
- canRerun : A boolean indicating whether the action can be rerun if it has already been executed. Defaults to false.
- cost :Relative cost of the action from 0-1. Defaults to 0.0.
- value : Relative value of performing the action from 0-1. Defaults to 0.0.
- toolGroups : Named tool groups the action requires.
- toolGroupRequirements : Tool group requirements with QoS constraints.

## 3.6.3. The @Condition annotation

The @Condition annotation is used to mark methods that evaluate conditions. They can take an OperationContext parameter
to access the blackboard and other infrastructure. If they take domain object parameters, the condition will
automatically be false until suitable instances are available.

<!-- image -->

Condition methods should not have side effects-for example, on the blackboard. This is important because they may be
called multiple times.

## 3.6.4. Parameters

@Action methods must have at least one parameter. @Condition methods must have zero or more parameters, but otherwise
follow the same rules as @Action methods regarding parameters. Ordering of parameters is not important.

Parameters fall in two categories:

- Domain objects . These are the normal inputs for action methods. They are backed by the blackboard and will be used as
  inputs to the action method. A nullable domain object parameter will be populated if it is non-null on the blackboard.
  This enables nice-to-have parameters that are not required for the action to run.
- Infrastructure objects . OperationContext parameters may be passed to action or condition methods.

<!-- image -->

Domain objects drive planning, specifying the preconditions to an action.

The ActionContext or ExecutingOperationContext subtype can be used in action methods. It adds asSubProcess methods that
can be used to run other agents in subprocesses. This is an important element of composition.

Use the least specific type possible for parameters. Use OperationContext unless you are creating a subprocess.

## 3.6.5. Binding by name

The @RequireNameMatch annotation can be used to bind parameters by name.

## 3.6.6. Handling of return types

Action methods normally return a single domain object.

Nullable return types are allowed. Returning null will trigger replanning. There may or not be an alternative path from
that point, but it won't be what the planner was trying to achieve.

There is a special case where the return type can essentially be a union type, where the action method can return one of
several types. This is achieved by a return type implementing the SomeOf tag interface. Implementations of this
interface can have multiple nullable fields. Any non-null values will be bound to the blackboard, and the postconditions
of the action will include all possible fields of the return type.

For example:

```
// Must implement the SomeOf interface data class FrogOrDog ( val frog : Frog ? = null , val dog : Dog ? = null , ) : SomeOf @Agent(description = "Illustrates use of the SomeOf interface") class ReturnsFrogOrDog { @Action fun frogOrDog (): FrogOrDog { return FrogOrDog (frog = Frog ("Kermit")) } // This works because the frog field of the return type was set @AchievesGoal(description = "Create a prince from a frog") @Action fun toPerson (frog: Frog ): PersonWithReverseTool { return PersonWithReverseTool (frog.name) } }
```

This enables routing scenarios in an elegant manner. Multiple fields of the SomeOf instance may be non-null and this is
not an error. It may enable the most appropriate routing.

## 3.6.7. Action method implementation

Embabel makes it easy to seamlessly integrate LLM invocation and application code, using common types. An @Action method
is a normal method, and can use any libraries or frameworks you like.

The only special thing about it is its ability to use the OperationContext parameter to access the blackboard and invoke
LLMs.

## 3.6.8. The @AchievesGoal annotation

The @AchievesGoal annotation can be added to an @Action method to indicate that the completion of the action achieves a
specific goal.

## 3.6.9. Implementing the StuckHandler interface

If an annotated agent class implements the StuckHandler interface, it can handle situations where an action is stuck
itself. For example, it can add data to the blackboard.

Example:

```
@Agent( description = "self unsticking agent", ) class SelfUnstickingAgent : StuckHandler { // The agent will get stuck as there's no dog to convert to a frog @Action @AchievesGoal(description = "the big goal in the sky") fun toFrog (dog: Dog ): Frog { return Frog (dog.name) } // This method will be called when the agent is stuck override fun handleStuck (agentProcess: AgentProcess ): StuckHandlerResult { called = true agentProcess. addObject ( Dog ("Duke")) return StuckHandlerResult ( message = "Unsticking myself", handler = this , code = StuckHandlingResultCode . REPLAN , agentProcess = agentProcess, ) } }
```

## 3.6.10. Advanced Usage: Nested processes

An @Action method can invoke another agent process. This is often done to use a stereotyped process that is composed
using the DSL.

Use the ActionContext.asSubProcess method to create a sub-process from the action context.

For example:

```
@Action fun report ( reportRequest: ReportRequest , context: ActionContext , ): ScoredResult < Report , SimpleFeedback > = context. asSubProcess ( // Will create an agent sub process with strong typing EvaluatorOptimizer . generateUntilAcceptable ( maxIterations = 5 , generator = { it. promptRunner (). withToolGroup ( CoreToolGroups . WEB ). create ( """ Given the topic, generate a detailed report in ${reportRequest.words} words. # Topic ${reportRequest.topic} # Feedback ${it.input ?: "No feedback provided"} """. trimIndent () ) }, evaluator = { it. promptRunner (). withToolGroup ( CoreToolGroups . WEB ). create ( """ Given the topic and word count, evaluate the report and provide feedback Feedback must be a score between 0 and 1, where 1 is perfect. # Report ${it.input.report} # Report request: ${reportRequest.topic} Word count: ${reportRequest.words} """. trimIndent () ) }, ))
```

## 3.7. DSL

You can also create agents using a DSL in Kotlin or Java.

This is useful for workflows where you want an atomic action that is complete in itself but may contain multiple steps
or actions.

## 3.7.1. Standard Workflows

There are a number of standard workflows, constructed by builders, that meet common requirements. These can be used to
create agents that will be exposed as Spring beans, or within @Action methods within other agents. All are type safe. As
far as possible, they use consistent APIs.

- SimpleAgentBuilder : The simplest agent, with no preconditions or postconditions.
- ScatterGatherBuilder : Fork join pattern for parallel processing.
- ConsensusBuilder :  A pattern for reaching consensus among multiple sources. Specialization of ScatterGather .
- RepeatUntil : Repeats a step until a condition is met.
- RepeatUntilAcceptable :  Repeats a step while a condition is met, with a separate evaluator providing feedback.

Creating a simple agent:

```
var agent = SimpleAgentBuilder .returning( Joke .class) ① .running(tac -> tac.ai() ② .withDefaultLlm() .createObject("Tell me a joke", Joke .class)) .buildAgent("joker", "This is guaranteed to return a dreadful joke"); ③
```

- ① Specify the return type.
- ② specify the action to run. Takes a SupplierActionContext&lt;RESULT&gt; OperationContext parameter allowing access to
  the current AgentProcess .
- ③ Build an agent with the given name and description.

A more complex example:

```
@Action FactChecks runAndConsolidateFactChecks ( DistinctFactualAssertions distinctFactualAssertions, ActionContext context) { var llmFactChecks = properties.models().stream() .flatMap(model -> factCheckWithSingleLlm(model, distinctFactualAssertions, context)) .toList(); return ScatterGatherBuilder ①
```

```
.returning( FactChecks .class) ② .fromElements( FactCheck .class) ③ .generatedBy(llmFactChecks) ④ .consolidatedBy( this ::reconcileFactChecks) ⑤ .asSubProcess(context); ⑥ }
```

- ① Start building a scatter gather agent.
- ② Specify the return type of the overall agent.
- ③ Specify the type of elements to be gathered.
- ④ Specify the list of functions to run in parallel, each generating an element, here of type FactCheck .
- ⑤ Specify a function to consolidate the results. In this case it will take a list of FactCheck and return a FactCheck
  and return a FactChecks object.
- ⑥ Build and run the agent as a subprocess of the current process. This is an alternative to asAgent shown in the
  SimpleAgentBuilder example. The API is consistent.

## 3.7.2. Registering Agent beans

Whereas the @Agent annotation causes a class to be picked up immediately by Spring, with the DSL you'll need an extra
step to register an agent with Spring.

Any bean of Agent type results in auto registration.

```
@Configuration class FactCheckerAgentConfiguration { @Bean fun factChecker (factCheckerProperties: FactCheckerProperties ): Agent { return factCheckerAgent ( llms = listOf ( LlmOptions ( AnthropicModels . CLAUDE_35_HAIKU ). withTemperature ( .3 ), LlmOptions ( AnthropicModels . CLAUDE_35_HAIKU ). withTemperature ( .0 ), ), properties = factCheckerProperties, ) } }
```

## 3.8. Core Types

## 3.8.1. LlmOptions

The LlmOptions class specifies which LLM to use and its hyperparameters. It's defined in the embabel-common project and
provides a fluent API for LLM configuration:

```
// Create LlmOptions with model and temperature var options = LlmOptions .withModel( OpenAiModels .GPT_4O_MINI) .withTemperature( 0.8 ); // Use different hyperparameters for different tasks var analyticalOptions = LlmOptions .withModel( OpenAiModels .GPT_4O_MINI) .withTemperature( 0.2 ) .withTopP( 0.9 );
```

## Important Methods:

- withModel(String) : Specify the model name
- withTemperature(Double) : Set creativity/randomness (0.0-1.0)
- withTopP(Double) : Set nucleus sampling parameter
- withTopK(Integer) : Set top-K sampling parameter
- withPersona(String) : Add a system message persona

## 3.8.2. PromptRunner

All LLM calls in user applications should be made via the PromptRunner interface. Once created, a PromptRunner can run
multiple prompts with the same LLM, hyperparameters, tool groups and PromptContributors .

## Getting a PromptRunner

You obtain a PromptRunner from an OperationContext using the fluent API:

```
@Action public Story createStory ( UserInput input, OperationContext context) { // Get PromptRunner with default LLM var runner = context.ai().withDefaultLlm(); // Get PromptRunner with specific LLM options var customRunner = context.ai().withLlm( LlmOptions .withModel( OpenAiModels .GPT_4O_MINI) .withTemperature( 0.8 ) ); return customRunner.createObject("Write a story about: " + input.getContent(), Story .class); }
```

## PromptRunner Methods

Core Object Creation:

- createObject(String, Class&lt;T&gt;) : Create a typed object from a prompt
- createObjectIfPossible(String, Class&lt;T&gt;) :  Try to create an object, return null on failure. This can cause
  replanning.
- generateText(String) : Generate simple text response

<!-- image -->

Normally you want to use one of the createObject methods to ensure the response is typed correctly.

## Tool and Context Management:

- withToolGroup(String) : Add tool groups for LLM access
- withToolObject(Object) : Add domain objects with @Tool methods
- withPromptContributor(PromptContributor) : Add context contributors

## LLM Configuration:

- withLlm(LlmOptions) : Use specific LLM configuration
- withGenerateExamples(Boolean) : Control example generation

## Returning a Specific Type

- creating(Class&lt;T&gt;) : Go into the ObjectCreator fluent API for returning a particular type.

For example:

```
var story = context.ai() .withDefaultLlm() .withToolGroup( CoreToolGroups .WEB) .creating( Story .class) .fromPrompt("Create a story about: " + input.getContent());
```

The main reason to do this is to add strongly typed examples for few-shot prompting. For example:

```
var story = context.ai() .withDefaultLlm() .withToolGroup( CoreToolGroups .WEB) .withExample("A children's story", new Story ("Once upon a time...")) ① .creating( Story .class) .fromPrompt("Create a story about: " + input.getContent());
```

- ① Example : The example will be included in the prompt in JSON format to guide the LLM.

## Advanced Features:

- withTemplate(String) : Use Jinja templates for prompts
- withSubagents(Subagent…) : Enable handoffs to other agents

- evaluateCondition(String, String) : Evaluate boolean condition === Tools

Tools can be passed to LLMs to allow them to perform actions.

Embabel provides tools to LLMs in several ways:

- At action or PromptRunner level, from a tool group or tool instance . A tool instance is an object with @Tool methods.
- At domain object level via @Tool methods authored by the application developer.

## 3.8.3. Implementing Tool Instances

Classes implementing tools can be stateful. They are often domain objects.

Return type restrictions: TODO

Method parameter restrictions: TODO

You can obtain the current AgentProcess in a Tool method implementation via AgentProcess.get() . This enables tools to
bind to the AgentProcess , making objects available to other actions. For example:

```
@Tool(description="My Tool") String bindCustomer ( Long id) { var customer = customerRepository.findById(id); var agentProcess = AgentProcess .get(); if (agentProcess != null ) { agentProcess.addObject(customer); return "Customer bound to blackboard"; } return "No agent process: Unable to bind customer"; }
```

## 3.8.4. Tool Groups

Embabel introduces the concept of a tool group . This is a level of indirection between user intent and tool selection.
For example, we don't ask for Brave or Google web search: we ask for "web" tools, which may be resolved differently in
different environments.

<!-- image -->

Tools use should be focused. Thus tool groups cannot be specified at agent level, but must be specified on individual
actions.

Tool groups are often backed by MCP.

## Configuring Tool Groups with Spring

Embabel uses Spring's @Configuration and @Bean annotations to expose ToolGroups to the agent platform. The framework
provides a default ToolGroupsConfiguration that demonstrates how to inject MCP servers and selectively expose MCP tools:

```
@Configuration class ToolGroupsConfiguration ( private val mcpSyncClients : List < McpSyncClient >, ) { @Bean fun mathToolGroup () = MathTools () @Bean fun mcpWebToolsGroup (): ToolGroup { return McpToolGroup ( description = CoreToolGroups . WEB_DESCRIPTION , name = "docker-web", provider = "Docker", permissions = setOf ( ToolGroupPermission . INTERNET_ACCESS ), clients = mcpSyncClients, filter = { // Only expose specific web tools, exclude rate-limited ones (it.toolDefinition. name (). contains ("brave") || it.toolDefinition. name (). contains ("fetch")) && !it.toolDefinition. name (). contains ("brave_local_search") } ) } }
```

## Key Configuration Patterns

MCP Client Injection: The configuration class receives a List&lt;McpSyncClient&gt; via constructor injection. Spring
automatically provides all available MCP clients that have been configured in the application.

Selective Tool Exposure: Each McpToolGroup uses a filter lambda to control which tools from the MCP servers are exposed
to agents. This allows fine-grained control over tool availability and prevents unwanted or problematic tools from being
used.

Tool Group Metadata: Tool groups include descriptive metadata like name , provider , and description to help agents
understand their capabilities. The permissions property declares what access the tool group requires (e.g.,
INTERNET\_ACCESS ).

## Creating Custom Tool Group Configurations

Applications can implement their own @Configuration classes to expose custom tool groups:

```
@Configuration public class MyToolGroupsConfiguration { @Bean public ToolGroup databaseToolsGroup ( DataSource dataSource) {
```

```
return new DatabaseToolGroup (dataSource); } @Bean public ToolGroup emailToolsGroup ( EmailService emailService) { return new EmailToolGroup (emailService); } }
```

This approach leverages Spring's dependency injection to provide tool groups with the services and resources they need,
while maintaining clean separation of concerns between tool configuration and agent logic.

## Tool Usage in Action Methods

The toolGroups parameter on @Action methods specifies which tool groups are required for that action to execute. The
framework automatically provides these tools to the LLM when the action runs.

Here's an example from the StarNewsFinder agent that demonstrates web tool usage:

```
// toolGroups specifies tools that are required for this action to run @Action(toolGroups = { CoreToolGroups .WEB}) public RelevantNewsStories findNewsStories ( StarPerson person, Horoscope horoscope, OperationContext context) { var prompt = """ %s is an astrology believer with the sign %s. Their horoscope for today is: <horoscope>%s</horoscope> Given this, use web tools and generate search queries to find %d relevant news stories summarize them in a few sentences. Include the URL for each story. Do not look for another horoscope reading or return results directly about astrology; find stories relevant to the reading above. """.formatted( person.name(), person.sign(), horoscope.summary(), storyCount); return context.ai().withDefaultLlm().createObject(prompt, RelevantNewsStories .class); }
```

Or in Kotlin:

```
// toolGroups specifies tools that are required for this action to run @Action(toolGroups = [ CoreToolGroups . WEB , CoreToolGroups . BROWSER_AUTOMATION ]) internal fun findNewsStories ( person: StarPerson , horoscope: Horoscope ,
```

```
context: OperationContext , ): RelevantNewsStories = context. ai (). withDefaultLlm () createObject ( """ ${person.name} is an astrology believer with the sign ${person.sign}. Their horoscope for today is: <horoscope>${horoscope.summary}</horoscope> Given this, use web tools and generate search queries to find $storyCount relevant news stories summarize them in a few sentences. Include the URL for each story. Do not look for another horoscope reading or return results directly about astrology; find stories relevant to the reading above. """. trimIndent () )
```

## Key Tool Usage Patterns

Tool Group Declaration: The toolGroups parameter on @Action methods explicitly declares which tool groups the action
needs. This ensures the LLM has access to the appropriate tools when executing that specific action.

Multiple Tool Groups: Actions can specify multiple tool groups  (
e.g., [CoreToolGroups.WEB, CoreToolGroups.BROWSER\_AUTOMATION] ) when they need different types of capabilities.

Automatic Tool Provisioning: The framework automatically makes the specified tools available to the LLM during the
action execution. Developers don't need to manually manage tool availability they simply declare what's needed.

Tool-Aware Prompts: Prompts should explicitly instruct the LLM to use the available tools. For example, "use web tools
and generate search queries" clearly directs the LLM to utilize the web search capabilities.

## Using Tools at PromptRunner Level

Instead of declaring tools at the action level, you can also specify tools directly on the PromptRunner for more
granular control:

```
// Add tool groups to a specific prompt context. promptRunner (). withToolGroup ( CoreToolGroups . WEB ). create ( """ Given the topic, generate a detailed report using web research. # Topic ${reportRequest.topic} """. trimIndent () ) // Add multiple tool groups context. ai (). withDefaultLlm ()
```

```
. withToolGroup ( CoreToolGroups . WEB ) . withToolGroup ( CoreToolGroups . MATH ) . createObject ("Calculate stock performance with web data", StockReport :: class )
```

## Adding Tool Objects with @Tool Methods:

You can also provide domain objects with @Tool methods directly to specific prompts:

```
context.ai() .withDefaultLlm() .withToolObject(jokerTool) .createObject("Create a UserInput object for fun", UserInput .class); // Add tool object with filtering and custom naming strategy context.ai() .withDefaultLlm() .withToolObject( ToolObject (calculatorService) .withNamingStrategy { "calc_$it" } .withFilter { methodName -> methodName.startsWith("compute") } ).createObject("Perform calculations", Result .class);
```

## Available PromptRunner Tool Methods:

- withToolGroup(String) : Add a single tool group by name
- withToolGroup(ToolGroup) : Add a specific ToolGroup instance
- withToolGroups(Set&lt;String&gt;) : Add multiple tool groups
- withTools(vararg String) : Convenient method to add multiple tool groups
- withToolObject(Any) : Add domain object with @Tool methods
- withToolObject(ToolObject) : Add ToolObject with custom configuration

## 3.8.5. Tool Objects

## 3.8.6. Tools on Domain Objects

Important

## 3.9. Structured Prompt Elements

Embabel provides a number of ways to structure and manage prompt content.

Prompt contributors are a fundamental way to structure and inject content into LLM prompts. You don't need to use
them-you can simply build your prompts as strings-but they can be useful to achieve consistency and reuse across
multiple actions or even across multiple agents using the same domain objects.

Prompt contributors implement the PromptContributor interface and provide text that gets included in the final prompt
sent to the LLM. By default the text will be included in the system prompt message.

## 3.9.1. The PromptContributor Interface and LlmReference Subinterface

All prompt contributors implement the PromptContributor interface with a contribution() method that returns a string to
be included in the prompt.

Add PromptContributor instances to a PromptRunner using the withPromptContributor() method.

A subinterface of PromptContributor is LlmReference .

An LlmReference is a prompt contributor that can also provide tools via annotated @Tool methods. So that tool naming can
be disambiguated, an LlmReference must also include name and description metadata.

Add LlmReference instances to a PromptRunner using the withReference() method.

Use LlmReference if:

- You want to provide both prompt content and tools from the same object
- You want to provide specific instructions on how to use these tools, beyond the individual tool descriptions
- Your data may be best exposed as either prompt content or tools, depending on the context. For example, if you have a
  list of 10 items you might just put in the prompt and say "Here are all the items: …". If you have a list of 10,000
  objects, you would include advice to use the tools to query them.

## 3.9.2. Built-in Convenience Classes

Embabel provides several convenience classes that implement PromptContributor for common use cases. These are optional
utilities - you can always implement the interface directly for custom needs.

## Persona

The Persona class provides a structured way to define an AI agent's personality and behavior:

```
val persona = Persona . create ( name = "Alex the Analyst", persona = "A detail-oriented data analyst with expertise in financial markets", voice = "Professional yet approachable, uses clear explanations", objective = "Help users understand complex financial data through clear analysis" )
```

This generates a prompt contribution like:

```
You are Alex the Analyst. Your persona: A detail-oriented data analyst with expertise in financial markets. Your objective is Help users understand complex financial data through clear analysis. Your voice: Professional yet approachable, uses clear explanations.
```

## RoleGoalBackstory

The RoleGoalBackstory class follows the Crew AI pattern and is included for users migrating from that framework:

```
var agent = RoleGoalBackstory .withRole("Senior Software Engineer") .andGoal("Write clean, maintainable code") .andBackstory("10+ years experience in enterprise software development")
```

This generates:

```
Role: Senior Software Engineer Goal: Write clean, maintainable code Backstory: 10+ years experience in enterprise software development
```

## 3.9.3. Custom PromptContributor Implementations

You can create custom prompt contributors by implementing the interface directly. This gives you complete control over
the prompt content:

```
class CustomSystemPrompt ( private val systemName : String ) : PromptContributor override fun contribution (): String { return "System: $systemName - Current time: ${LocalDateTime.now()}" } } class ConditionalPrompt ( private val condition : () -> Boolean , private val trueContent : String , private val falseContent : String ) : PromptContributor { override fun contribution (): String { return if ( condition ()) trueContent else falseContent } }
```

```
{
```

## 3.9.4. Examples from embabel-agent-examples

The embabel-agent-examples repository demonstrates various agent development patterns and Spring Boot integration
approaches for building AI agents with Embabel.

## 3.9.5. Best Practices

- Keep prompt contributors focused and single-purpose
- Use the convenience classes ( Persona , RoleGoalBackstory ) when they fit your needs
- Implement custom PromptContributor classes for domain-specific requirements
- Consider using dynamic contributors for context-dependent content
- Test your prompt contributions to ensure they produce the desired LLM behavior === Templates

Embabel supports Jinja templates for generating prompts. You do this via the PromptRunner.withTemplate(String) method.

This method takes a Spring resource path to a Jinja template. The default location is under classpath:/prompts/ and the
.jinja extension is added automatically.

You can also specify a full resource path with Spring resource conventions.

Once you have specified the template, you can create objects using a model map.

An example:

```
val distinctFactualAssertions = context.ai() .withLlm(properties.deduplicationLlm()) // Jinjava template from classpath at prompts/factchecker/consolidate_assertions.jinja .withTemplate("factchecker/consolidate_assertions") .createObject( DistinctFactualAssertions .class, Map .of( "assertions", allAssertions, "reasoningWordCount", properties.reasoningWordCount() ) );
```

<!-- image -->

Don't rush to externalize prompts. In modern languages with multi-line strings, it's often easier to keep prompts in the
codebase. Externalizing them can sacrifice type safety and lead to complexity and maintenance challenges.

## 3.10. The AgentProcess

An AgentProcess is created every time an agent is run. It has a unique id.

## 3.11. ProcessOptions

Agent processes can be configured with ProcessOptions .

ProcessOptions controls:

- contextId : An identifier of any existing context in which the agent is running.
- blackboard : The blackboard to use for the agent. Allows starting from a particular state.
- test : Whether the agent is running in test mode.
- verbosity :  The verbosity level of the agent. Allows fine grained control over logging prompts, LLM returns and
  detailed planning information
- control : Control options, determining whether the agent should be terminated as a last resort. EarlyTerminationPolicy
  can based on an absolute number of actions or a maximum budget.
- Delays: Both operations (actions) and tools can have delays. This is useful to avoid rate limiting.

## 3.12. The AgentPlatform

An AgentPlatform provides the ability to run agents in a specific environment. This is an SPI interface, so multiple
implementations are possible.

## 3.13. Invoking Embabel Agents

While many examples show Embabel agents being invoked via UserInput through the Embabel shell, they can also be invoked
programmatically with strong typing.

This is usually how they're used in web applications. It is also the most deterministic approach as code, rather than
LLM assessment of user input, determines which agent is invoked and how.

## 3.13.1. Creating an AgentProcess Programmatically

You can create and execute agent processes directly using the AgentPlatform :

```
// Create an agent process with bindings val agentProcess = agentPlatform. createAgentProcess ( agent = myAgent, processOptions = ProcessOptions (), bindings = mapOf ("input" to userRequest) ) // Start the process and wait for completion val result = agentPlatform. start (agentProcess). get () // Or run synchronously val completedProcess = agentProcess. run () val result = completedProcess.last< MyResultType >()
```

You can create processes and populate their input map from varargs objects:

```
// Create process from objects (like in web controllers)
```

```
val agentProcess = agentPlatform. createAgentProcessFrom ( agent = travelAgent, processOptions = ProcessOptions (), travelRequest, userPreferences )
```

## 3.13.2. Using AgentInvocation

AgentInvocation provides a higher-level, type-safe API for invoking agents. It automatically finds the appropriate agent
based on the expected result type.

## Basic Usage

```
Java // Simple invocation with explicit result type var invocation = AgentInvocation .create(agentPlatform, TravelPlan .class); TravelPlan plan = invocation.invoke(travelRequest); Kotlin // Type-safe invocation with inferred result type val invocation : AgentInvocation < TravelPlan > = AgentInvocation . create (agentPlatform) val plan = invocation. invoke (travelRequest)
```

## Invocation with Named Inputs

```
// Invoke with a map of named inputs Map < String , Object > inputs = Map .of( "request", travelRequest, "preferences", userPreferences ); TravelPlan plan = invocation.invoke(inputs);
```

## Custom Process Options

Configure verbosity, budget, and other execution options:

```
Java var invocation = AgentInvocation .builder(agentPlatform) .options(options -> options .verbosity(verbosity -> verbosity .showPrompts( true ) .showResponses( true ) .debug( true ))) .build( TravelPlan .class); TravelPlan plan = invocation.invoke(travelRequest); Kotlin val processOptions = ProcessOptions ( verbosity = Verbosity ( showPrompts = true , showResponses = true , debug = true ) ) val invocation : AgentInvocation < TravelPlan > = AgentInvocation . builder (agentPlatform) . options (processOptions) . build () val plan = invocation. invoke (travelRequest)
```

## Asynchronous Invocation

For long-running operations, use async invocation:

```
CompletableFuture < TravelPlan > future = invocation.invokeAsync(travelRequest); // Handle result when complete future.thenAccept(plan -> { logger.info("Travel plan generated: {}", plan); }); // Or wait for completion TravelPlan plan = future.get();
```

## Agent Selection

AgentInvocation automatically finds agents by examining their goals:

- Searches all registered agents in the platform
- Finds agents with goals that produce the requested result type
- Uses the first matching agent found
- Throws an error if no suitable agent is available

## Real-World Web Application Example

Here's how AgentInvocation is used in the Tripper travel planning application with htmx for asynchronous UI updates:

```
@Controller class TripPlanningController ( private val agentPlatform : AgentPlatform ) { private val activeJobs = ConcurrentHashMap < String , CompletableFuture < TripPlan >>() @PostMapping("/plan-trip") fun planTrip ( @ModelAttribute tripRequest: TripRequest , model: Model ): String { // Generate unique job ID for tracking val jobId = UUID . randomUUID (). toString () // Create agent invocation with custom options val invocation : AgentInvocation < TripPlan > = AgentInvocation . builder (agentPlatform) . options { options -> options. verbosity { verbosity -> verbosity. showPrompts ( true ) . showResponses ( false ) . debug ( false ) } } . build () // Start async agent execution val future = invocation. invokeAsync (tripRequest) activeJobs[jobId] = future // Set up completion handler future. whenComplete { result, throwable -> if (throwable != null ) { logger. error ("Trip planning failed for job $jobId", throwable) } else { logger. info ("Trip planning completed for job $jobId") } }
```

```
model. addAttribute ("jobId", jobId) model. addAttribute ("tripRequest", tripRequest) // Return htmx template that will poll for results return "trip-planning-progress" } @GetMapping("/trip-status/{jobId}") @ResponseBody fun getTripStatus (@PathVariable jobId: String ): ResponseEntity < Map < String , Any >> { val future = activeJobs[jobId] ?: return ResponseEntity . notFound (). build () return when { future.isDone -> { try { val tripPlan = future. get () activeJobs. remove (jobId) ResponseEntity . ok ( mapOf ( "status" to "completed", "result" to tripPlan, "redirect" to "/trip-result/$jobId" )) } catch (e: Exception ) { activeJobs. remove (jobId) ResponseEntity . ok ( mapOf ( "status" to "failed", "error" to e.message )) } } future.isCancelled -> { activeJobs. remove (jobId) ResponseEntity . ok ( mapOf ("status" to "cancelled")) } else -> { ResponseEntity . ok ( mapOf ( "status" to "in_progress", "message" to "Planning your amazing trip..." )) } } } @GetMapping("/trip-result/{jobId}") fun showTripResult ( @PathVariable jobId: String , model: Model ): String {
```

```
// Retrieve completed result from cache or database val tripPlan = tripResultCache[jobId] ?: return "redirect:/error" model. addAttribute ("tripPlan", tripPlan) return "trip-result" } @DeleteMapping("/cancel-trip/{jobId}") @ResponseBody fun cancelTrip (@PathVariable jobId: String ): ResponseEntity < Map < String , String >> { val future = activeJobs[jobId] return if (future != null && !future.isDone) { future. cancel ( true ) activeJobs. remove (jobId) ResponseEntity . ok ( mapOf ("status" to "cancelled")) } else { ResponseEntity . badRequest () . body ( mapOf ("error" to "Job not found or already completed")) } } companion object { private val logger = LoggerFactory . getLogger ( TripPlanningController :: class .java) private val tripResultCache = ConcurrentHashMap < String , TripPlan >() } }
```

## Key Patterns:

- Async Execution : Uses invokeAsync() to avoid blocking the web request
- Job Tracking : Maintains a map of active futures for status polling
- htmx Integration : Returns status updates that htmx can consume for UI updates
- Error Handling : Proper exception handling and user feedback
- Resource Cleanup : Removes completed jobs from memory
- Process Options : Configures verbosity and debugging for production use

<!-- image -->

Agents can also be exposed as MCP servers and consumed from tools like Claude Desktop.

## 3.14. API vs SPI

Embabel makes a clean distinction between its API and SPI. The API is the public interface that users interact with,
while the SPI (Service Provider Interface) is intended for developers who want to extend or customize the behavior of
Embabel, or platform providers.

<!-- image -->

Application code should only depend on the API, not the SPI. The SPI is subject to change and should not be used in
production code.

## 3.15. Embabel and Spring

Embabel embraces Spring.

It is built on Spring AI, and leverages Spring for configuration and management.

We recommend using Spring Boot for building Embabel applications, as it provides a familiar environment for most Java
developers.

## 3.16. Working with LLMs

Embabel supports any LLM supported by Spring AI. In practice, this is just about any LLM.

## 3.16.1. Choosing an LLM

Embabel encourages you to think about LLM choice for every LLM invocation. The PromptRunner interface makes this easy.
Because Embabel enables you to break agentic flows up into multiple action steps, each step can use a smaller, focused
prompt with fewer tools. This means it may be able to use a smaller LLM.

## Considerations:

- Consider the complexity of the return type you expect from the LLM. This is typically a good proxy for determining
  required LLM quality. A small LLM is likely to struggle with a deeply nested return structure.
- Consider the nature of the task. LLMs have different strengths; review any available documentation. You don't
  necessarily need a huge, expensive model that is good at nearly everything, at the cost of your wallet and the
  environment.
- Consider the sophistication of tool calling required . Simple tool calls are fine, but complex orchestration is
  another indicator you'll need a strong LLM. (It may also be an indication that you should create a more sophisticated
  flow using Embabel GOAP.)
- Consider trying a local LLM running under Ollama or Docker.

<!-- image -->

Trial and error is your friend. Embabel makes it easy to switch LLMs; try the cheapest thing that could work and switch
if it doesn't.

## 3.17. Customizing Embabel

## 3.17.1. Adding LLMs

You can add custom LLMs as Spring beans of type Llm .

Llms are created around Spring AI ChatModel instances.

A common requirement is to add an open AI compatible LLM. This can be done by extending the OpenAiCompatibleModelFactory
class as follows:

```
@Configuration class CustomOpenAiCompatibleModels ( @Value("\${MY_BASE_URL:#{null}}") baseUrl: String ?, @Value("\${MY_API_KEY}") apiKey: String , observationRegistry: ObservationRegistry , ) : OpenAiCompatibleModelFactory (baseUrl = baseUrl, apiKey = apiKey, observationRegistry = observationRegistry) { @Bean fun myGreatModel (): Llm { // Call superclass method return openAiCompatibleLlm ( model = "my-great-model", provider = "me", knowledgeCutoffDate = LocalDate . of ( 2025 , 1 , 1 ), pricingModel = PerTokenPricingModel ( usdPer1mInputTokens = .40 , usdPer1mOutputTokens = 1.6 , ) ) } }
```

## 3.17.2. Adding embedding models

Embedding models can also be added as beans of type EmbeddingService .

## 3.17.3. Configuration via application.properties or application.yml

You can specify Spring configuration, your own configuration and Embabel configuration in the regular Spring
configuration files. Profile usage will work as expected.

## 3.17.4. Customizing logging

You can customize logging as in any Spring application.

For example, in application.properties you can set properties like:

```
logging.level.com.embabel.agent.a2a =DEBUG
```

## 3.18. Integrations

3.18.1. MCP

3.18.2. A2A

## 3.19. Testing

Like Spring, Embabel facilitates testing of user applications. The framework provides comprehensive testing support for
both unit and integration testing scenarios.

<!-- image -->

Building Gen AI applications is no different from building other software. Testing is critical to delivering quality
software and must be considered from the outset.

## 3.19.1. Unit Testing

Unit testing in Embabel enables testing individual agent actions without involving real LLM calls.

Embabel's design means that agents are usually POJOs that can be instantiated with fake or mock objects. Actions are
methods that can be called directly with test fixtures. In additional to your domain objects, you will pass a text
fixture for the Embabel OperationContext , enabling you to intercept and verify LLM calls.

The framework provides FakePromptRunner and FakeOperationContext to mock LLM interactions while allowing you to verify
prompts, hyperparameters, and business logic. Alternatively you can use mock objects. Mockito is the default choice for
Java; mockk for Kotlin.

## Java Example: Testing Prompts and Hyperparameters

Here's a unit test from the Java Agent Template repository, using Embabel fake objects:

```
class WriteAndReviewAgentTest { @Test void testWriteAndReviewAgent () { var context = FakeOperationContext .create(); var promptRunner = ( FakePromptRunner ) context.promptRunner(); context.expectResponse( new Story ("One upon a time Sir Galahad . . ")); var agent = new WriteAndReviewAgent ( 200 , 400 ); agent.craftStory( new UserInput ("Tell me a story about a brave knight", Instant .now()), context); String prompt = promptRunner.getLlmInvocations().getFirst().getPrompt(); assertTrue(prompt.contains("knight"), "Expected prompt to contain 'knight'"); var temp = promptRunner.getLlmInvocations().getFirst().getInteraction(). getLlm().getTemperature();
```

```
assertEquals( 0.9 , temp, 0.01 , "Expected temperature to be 0.9: Higher for more creative output"); } @Test void testReview () { var agent = new WriteAndReviewAgent ( 200 , 400 ); var userInput = new UserInput ("Tell me a story about a brave knight", Instant .now()); var story = new Story ("Once upon a time, Sir Galahad..."); var context = FakeOperationContext .create(); context.expectResponse("A thrilling tale of bravery and adventure!"); agent.reviewStory(userInput, story, context); var promptRunner = ( FakePromptRunner ) context.promptRunner(); String prompt = promptRunner.getLlmInvocations().getFirst().getPrompt(); assertTrue(prompt.contains("knight"), "Expected review prompt to contain 'knight'"); assertTrue(prompt.contains("review"), "Expected review prompt to contain 'review'"); } }
```

## Kotlin Example: Testing Prompts and Hyperparameters

Here's the unit test from the Kotlin Agent Template repository:

```
/** * Unit tests for the WriteAndReviewAgent class. * Tests the agent's ability to craft and review stories based on user input. */ internal class WriteAndReviewAgentTest { /** * Tests the story crafting functionality of the WriteAndReviewAgent. * Verifies that the LLM call contains expected content and configuration. */ @Test fun testCraftStory () { // Create agent with word limits: 200 min, 400 max val agent = WriteAndReviewAgent ( 200 , 400 ) val context = FakeOperationContext . create () val promptRunner = context. promptRunner () as FakePromptRunner context. expectResponse ( Story ("One upon a time Sir Galahad . . ")) agent. craftStory ( UserInput ("Tell me a story about a brave knight", Instant . now ()), context )
```

```
// Verify the prompt contains the expected keyword Assertions . assertTrue ( promptRunner.llmInvocations. first ().prompt. contains ("knight"), "Expected prompt to contain 'knight'" ) // Verify the temperature setting for creative output val actual = promptRunner.llmInvocations. first ().interaction.llm.temperature Assertions . assertEquals ( 0.9 , actual, 0.01 , "Expected temperature to be 0.9: Higher for more creative output" ) } @Test fun testReview () { val agent = WriteAndReviewAgent ( 200 , 400 ) val userInput = UserInput ("Tell me a story about a brave knight", Instant . now ()) val story = Story ("Once upon a time, Sir Galahad...") val context = FakeOperationContext . create () context. expectResponse ("A thrilling tale of bravery and adventure!") agent. reviewStory (userInput, story, context) val promptRunner = context. promptRunner () as FakePromptRunner val prompt = promptRunner.llmInvocations. first ().prompt Assertions . assertTrue (prompt. contains ("knight"), "Expected review prompt to contain 'knight'") Assertions . assertTrue (prompt. contains ("review"), "Expected review prompt to contain 'review'") // Verify single LLM invocation during review Assertions . assertEquals ( 1 , promptRunner.llmInvocations.size) } }
```

## Key Testing Patterns Demonstrated

## Testing Prompt Content:

- Use context.getLlmInvocations().getFirst().getPrompt() to get the actual prompt sent to the LLM
- Verify that key domain data is properly included in the prompt using assertTrue(prompt.contains(…))

## Testing Tool Group Configuration:

- Access tool groups via getInteraction().getToolGroups()

- Verify expected tool groups are present or absent as required

## Testing with Spring Dependencies:

- Mock Spring-injected services like HoroscopeService using standard mocking frameworks - Pass mocked dependencies to
  agent constructor for isolated unit testing

## Testing Multiple LLM Interactions

```
@Test void shouldHandleMultipleLlmInteractions () { // Arrange var input = new UserInput ("Write about space exploration"); var story = new Story ("The astronaut gazed at Earth..."); ReviewedStory review = new ReviewedStory ("Compelling narrative with vivid imagery."); // Set up expected responses in order context.expectResponse(story); context.expectResponse(review); // Act var writtenStory = agent.writeStory(input, context); ReviewedStory reviewedStory = agent.reviewStory(writtenStory, context); // Assert assertEquals(story, writtenStory); assertEquals(review, reviewedStory); // Verify both LLM calls were made List < LlmInvocation > invocations = context.getLlmInvocations(); assertEquals( 2 , invocations.size()); // Verify first call (writer) var writerCall = invocations.get( 0 ); assertEquals( 0.8 , writerCall.getInteraction().getLlm().getTemperature(), 0.01 ); // Verify second call (reviewer) var reviewerCall = invocations.get( 1 ); assertEquals( 0.2 , reviewerCall.getInteraction().getLlm().getTemperature(), 0.01 ); }
```

You can also use Mockito or mockk directory. Consider this component, using direct injection of Ai :

```
@Component public record InjectedComponent ( Ai ai) { public record Joke ( String leadup, String punchline) { }
```

```
public String tellJokeAbout ( String topic) { return ai .withDefaultLlm() .generateText("Tell me a joke about " + topic); } }
```

A unit test using Mockito to verify prompt and hyperparameters:

```
class InjectedComponentTest { @Test void testTellJokeAbout () { var mockAi = Mockito .mock( Ai .class); var mockPromptRunner = Mockito .mock( PromptRunner .class); var prompt = "Tell me a joke about frogs"; // Yep, an LLM came up with this joke. var terribleJoke = """ Why don't frogs ever pay for drinks? Because they always have a tadpole in their wallet! """; when(mockAi.withDefaultLlm()).thenReturn(mockPromptRunner); when(mockPromptRunner.generateText(prompt)).thenReturn(terribleJoke); var injectedComponent = new InjectedComponent (mockAi); var joke = injectedComponent.tellJokeAbout("frogs"); assertEquals(terribleJoke, joke); Mockito .verify(mockAi).withDefaultLlm(); Mockito .verify(mockPromptRunner).generateText(prompt); } }
```

## 3.19.2. Integration Testing

Integration testing exercises complete agent workflows with real or mock external services while still avoiding actual
LLM calls for predictability and speed.

This can ensure:

- Agents are picked up by the agent platform
- Data flow is correct within agents
- Failure scenarios are handled gracefully
- Agents interact correctly with each other and external systems

- The overall workflow behaves as expected
- LLM prompts and hyperparameters are correctly configured

Embabel integration testing is built on top of Spring's excellent integration testing support, thus allowing you to work
with real databases if you wish. Spring's integration with Testcontainers is particularly userul.

## Using EmbabelMockitoIntegrationTest

Embabel provides EmbabelMockitoIntegrationTest as a base class that simplifies integration testing with convenient
helper methods:

```
/** * Use framework superclass to test the complete workflow of writing and reviewing a story. * This will run under Spring Boot against an AgentPlatform instance * that has loaded all our agents. */ class StoryWriterIntegrationTest extends EmbabelMockitoIntegrationTest { @Test void shouldExecuteCompleteWorkflow () { var input = new UserInput ("Write about artificial intelligence"); var story = new Story ("AI will transform our world..."); var reviewedStory = new ReviewedStory (story, "Excellent exploration of AI themes.", Personas .REVIEWER); whenCreateObject(contains("Craft a short story"), Story .class) .thenReturn(story); // The second call uses generateText whenGenerateText(contains("You will be given a short story to review")) .thenReturn(reviewedStory.review()); var invocation = AgentInvocation .create(agentPlatform, ReviewedStory .class); var reviewedStoryResult = invocation.invoke(input); assertNotNull(reviewedStoryResult); assertTrue(reviewedStoryResult.getContent().contains(story.text()), "Expected story content to be present: " + reviewedStoryResult .getContent()); assertEquals(reviewedStory, reviewedStoryResult, "Expected review to match: " + reviewedStoryResult); verifyCreateObjectMatching(prompt -> prompt.contains("Craft a short story"), Story .class, llm -> llm.getLlm().getTemperature() == 0.9 && llm.getToolGroups ().isEmpty()); verifyGenerateTextMatching(prompt -> prompt.contains("You will be given a short story to review"));
```

```
verifyNoMoreInteractions(); } }
```

## Key Integration Testing Features

Base Class Benefits: -EmbabelMockitoIntegrationTest handles Spring Boot setup and LLM mocking automatically - Provides
agentPlatform and llmOperations pre-configured - Includes helper methods for common testing patterns

Convenient Stubbing Methods: -whenCreateObject(prompt, outputClass) :  Mock object creation calls whenGenerateText(
prompt) :  Mock text generation calls - Support for both exact prompts and contains() matching

Advanced Verification: -verifyCreateObjectMatching() :  Verify prompts with custom matchers
verifyGenerateTextMatching() :  Verify text generation calls verifyNoMoreInteractions() :  Ensure no unexpected LLM
calls

LLM Configuration Testing: - Verify temperature settings: llm.getLlm().getTemperature() == 0.9 -Check tool groups:
llm.getToolGroups().isEmpty() - Validate persona and other LLM options

## 3.20. Embabel Architecture

## 3.21. Troubleshooting

This section covers common issues you might encounter when developing with Embabel and provides practical solutions.

## 3.21.1. Common Problems and Solutions

| Problem                             | Solution                                                                                                                                                                                                                                                                                                                                                                      | Related Docs    |
|-------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------|
| Compilation Error                   | Check that you're using the correct version of Embabel in your Maven or Gradle dependencies. You may be using an API from a later version (even a snapshot). Version mismatches between different Embabel modules can cause compilation issues. Ensure all  com.embabel.agent artifacts use the same version, unless you're following a specific example that does otherwise. | Configuration   |
| Don't Know How to Invoke Your Agent | Look at examples of processing  UserInput  in the documentation. Study  AgentInvocation  patterns to understand how to trigger your agent flows. The key is understanding how to provide the initial input that your agent expects.                                                                                                                                           | Invoking Agents |

| Problem                                                  | Solution                                                                                                                                                                                                                                                                                                                                 | Related Docs               |
|----------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------|
| Agent Flow Not Completing                                | This usually indicates a data flow problem. First, understand Embabel's type-driven data flow concepts - review how input/output types create dependencies between actions. Then write an integration test to verify your flow works end-to- end. Familiarize yourself with Embabel's GOAP planning concept.                             | Data Flow Concepts         |
| LLM Prompts Look Wrong or Have Incorrect Hyperparameters | Write unit tests to capture and verify the exact prompts being sent to your LLM. This allows you to see the actual prompt content and tune temperature, model selection, and other parameters. Unit testing is the best way to debug LLM interactions.                                                                                   | Testing                    |
| Agent Gets Stuck in Planning                             | Check that all your actions have clear input/output type signatures. Missing or circular dependencies in your type flow can prevent the planner from finding a valid path to the goal. Review your  @Action  method signatures. Look at the log output from the planner for clues. Set your  ProcessOptions.verbosity  to show planning. | Type-Driven Flow           |
| Tools Not Available to Agent                             | Ensure you've specified the correct  toolGroups  in your  @Action  annotation. Tools must be explicitly declared for the action to access them. Check that required tool groups are available in your environment.                                                                                                                       | Tools                      |
| Agent Runs But Produces Poor Results                     | Review your prompt engineering and persona configuration. Consider adjusting LLM temperature, model selection, and context provided to actions. Write tests to capture actual vs expected outputs.                                                                                                                                       | Testing, LLM Configuration |
| You're Struggling to Express What You Want in a Plan     | Familiarize yourself with custom conditions for complex flow control. For common behavior patterns, consider using atomic actions with Embabel's typesafe custom builders such as ScatterGatherBuilder  and  RepeatUntilBuilder instead of trying to express everything through individual actions.                                      | DSL and Builders           |
| Your Agent Has No Goals and Cannot Execute               | Look at the  @AchievesGoal  annotation and ensure your terminal action is annotated with it. Every agent needs at least one action marked with @AchievesGoal  to define what constitutes completion of the agent's work.                                                                                                                 | Annotations                |

| Problem                                                                                                  | Solution                                                                                                                                                                                                                                                                                                                | Related Docs              |
|----------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------|
| Your Agent Isn't Visible to an MCP Client Like Claude Desktop                                            | Ensure that your  @AchievesGoal  annotation includes  @Export(remote=true) . This makes your agent available for remote invocation through MCP (Model Context Protocol) clients.                                                                                                                                        | Annotations, Integrations |
| Your Agent Can't Use Upstream MCP Tools and You're Seeing Errors in Logs About Possible Misconfiguration | Check that your Docker configuration is correct if using the default Docker MCP Gateway. Verify that Docker containers are running and accessible. For other MCP configurations, ensure your Spring AI MCP client configuration is correct. See the Spring AI MCP client documentation for detailed setup instructions. | Spring AI MCP Client      |

## 3.21.2. Debugging Strategies

## Enable Debug Logging

Customize Embabel logging in application.yml or application.properties to see detailed agent execution. For example:

```
logging: level: com.embabel.agent: DEBUG
```

## 3.21.3. Getting Help

The Embabel community is active and helpful. Join our Discord server to ask questions and share experiences.

## 3.22. Migrating from other frameworks

Many people start their journey with Python frameworks.

This section covers how to migrate from popular frameworks when it's time to use a more robust and secure platform with
access to existing code and services.

## 3.22.1. Migrating from CrewAI

CrewAI uses a collaborative multi-agent approach where agents work together on tasks. Embabel provides similar
capabilities with stronger type safety and better integration with existing Java/Kotlin codebases.

## Core Concept Mapping

| CrewAI Concept                  | Embabel Equivalent                                                                                           | Notes                                         |
|---------------------------------|--------------------------------------------------------------------------------------------------------------|-----------------------------------------------|
| Agent Role/Goal/Backstory       | RoleGoalBackstory  PromptContributor                                                                         | Convenience class for agent personality       |
| Sequential Tasks                | Typed data flow between actions                                                                              | Type-driven execution with automatic planning |
| Crew (Multi-agent coordination) | Actions with shared PromptContributors                                                                       | Agents can adopt personalities as needed      |
| YAML Configuration              | Standard Spring @ConfigurationProperties  backed by application.yml  or profile-specific configuration files | Type-safe configuration with validation       |

## Migration Example

## CrewAI Pattern:

```
research_agent = Agent( role='Research Specialist', goal='Find comprehensive information', backstory='Expert researcher with 10+ years experience' ) writer_agent = Agent( role='Content Writer', goal='Create engaging content', backstory='Professional writer specializing in technical content' ) crew = Crew( agents=[research_agent, writer_agent], tasks=[research_task, write_task], process=Process.sequential )
```

## Embabel Equivalent:

```
@ConfigurationProperties("examples.book-writer") record BookWriterConfig ( LlmOptions researcherLlm, LlmOptions writerLlm, RoleGoalBackstory researcher, RoleGoalBackstory writer
```

```
) {} @Agent(description = "Write a book by researching, outlining, and writing chapters") public record BookWriter ( BookWriterConfig config) { @Action ResearchReport researchTopic ( BookRequest request, OperationContext context) { return context.ai() .withLlm(config.researcherLlm()) .withPromptElements(config.researcher(), request) .withToolGroup( CoreToolGroups .WEB) .createObject("Research the topic thoroughly...", ResearchReport .class); } @Action BookOutline createOutline ( BookRequest request, ResearchReport research, OperationContext context) { return context.ai() .withLlm(config.writerLlm()) .withPromptElements(config.writer(), request, research) .createObject("Create a book outline...", BookOutline .class); } @AchievesGoal(export = @Export(remote = true )) @Action Book writeBook ( BookRequest request, BookOutline outline, OperationContext context) { // Parallel chapter writing with crew-like coordination var chapters = context.parallelMap(outline.chapterOutlines(), config.maxConcurrency(), chapterOutline -> writeChapter(request, outline, chapterOutline, context)); return new Book (request, outline.title(), chapters); } }
```

## Key Advantages:

- Type Safety : Compile-time validation of data flow
- Spring Integration : Leverage existing enterprise infrastructure
- Automatic Planning :  GOAP planner handles task sequencing, and is capable of more sophisticated planning
- Tool Integration with the JVM : Native access to existing Java/Kotlin services

## 3.22.2. Migrating from Pydantic AI

Pydantic AI provides a Python framework for building AI agents with type safety and validation. Embabel offers similar
capabilities in the JVM ecosystem with stronger integration into enterprise environments.

## Core Concept Mapping

| Pydantic AI Concept                                                                         | Embabel Equivalent                                                                       | Notes                                          |
|---------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------|------------------------------------------------|
| @system_prompt decorator                                                                    | PromptContributor classes                                                                | More flexible and composable prompt management |
| @tool decorator                                                                             | Equivalent  @Tool  annotated methods can be included on agent classes and domain objects | Agent class                                    |
| @Agent  annotated record/class                                                              | Declarative agent definition with Spring integration                                     | RunContext                                     |
| Blackboard state, accessible via OperationContext  but normally not a concern for user code | SystemPrompt                                                                             | Custom PromptContributor                       |
| Structured prompt contribution system                                                       | deps parameter                                                                           | Spring dependency injection                    |

## Migration Example

## Pydantic AI Pattern:

```
# Based on https://ai.pydantic.dev/examples/bank-support/ from pydantic_ai import Agent, RunContext from pydantic_ai.tools import tool @system_prompt def support_prompt () -> str: return "You are a support agent in our bank" @tool async def get_customer_balance (customer_id: int, include_pending: bool = False) -> float: # Database lookup customer = find_customer(customer_id) return customer.balance + (customer.pending if include_pending else 0 ) agent = Agent( 'openai:gpt-4-mini', system_prompt=support_prompt, tools=[get_customer_balance], ) result = agent.run("What's my balance?", deps={'customer_id': 123 })
```

## Embabel Equivalent:

```
// From embabel-agent-examples/examplesjava/src/main/java/com/embabel/example/pydantic/banksupport/SupportAgent.java record Customer ( Long id, String name, float balance, float pendingAmount) { @Tool(description = "Find the balance of a customer by id") float balance ( boolean includePending) { return includePending ? balance + pendingAmount : balance; } } record SupportInput ( @JsonPropertyDescription("Customer ID") Long customerId, @JsonPropertyDescription("Query from the customer") String query) { } record SupportOutput ( @JsonPropertyDescription("Advice returned to the customer") String advice, @JsonPropertyDescription("Whether to block their card or not") boolean blockCard, @JsonPropertyDescription("Risk level of query") int risk) { } @Agent(description = "Customer support agent") record SupportAgent ( CustomerRepository customerRepository) { @AchievesGoal(description = "Help bank customer with their query") @Action SupportOutput supportCustomer ( SupportInput supportInput, OperationContext context) { var customer = customerRepository.findById(supportInput.customerId()); if (customer == null ) { return new SupportOutput ("Customer not found with this id", false , 0 ); } return context.ai() .withLlm( OpenAiModels .GPT_41_MINI) .withToolObject(customer) .createObject( """ You are a support agent in our bank, give the customer support and judge the risk level of their query. In some cases, you may need to block their card. In this case, explain why. Reply using the customer's name, "%s". Currencies are in $. Their query: [%s] """.formatted(customer.name(), supportInput.query()), SupportOutput .class); } }
```

## Key Advantages:

- Enterprise Integration : Native Spring Boot integration with existing services
- Compile-time Safety : Strong typing catches errors at build time
- Automatic Planning : GOAP planner handles complex multi-step operations
- JVM Ecosystem : Access to mature libraries and enterprise infrastructure

## 3.22.3. Migrating from LangGraph

tbd

## 3.22.4. Migrating from Google ADK

tbd

## 3.23. API Evolution

While Embabel is still pre-GA, we strive to avoid breaking changes.

Because Embabel builds on Spring's POJO support, framework code dependencies are localized and minimized.

The key surface area is the Ai and PromptRunner interfaces, which we will strive to avoid breaking.

For maximum stability:

- Always use the latest stable version rather than a snapshot. Snapshots may change frequently.
- Avoid using types under the com.embabel.agent.experimental package.
- Avoid using any method or class marked with the @ApiStatus.Experimental or @ApiStatus.Internal annotations.

<!-- image -->

application code should not depend on any types under the com.embabel.agent.spi package. This is intended for provision
of runtime infrastructure only, and may change without notice.

## Chapter 4. Design Considerations

Embabel is designed to give you the ability to determine the correct balance between LLM autonomy and control from code.
This section discusses the design considerations that you can use to achieve this balance.

## 4.1. Domain objects

A rich domain model helps build a good agentic system. Domain objects should not merely contain state, but also expose
behavior. Avoid the anemic domain model. Domain objects have multiple roles:

1. Ensuring type safety and toolability. Code can access their state; prompts will be strongly typed; and LLMs know what
   to return.
2. Exposing behavior to call in code , exactly as in any well-designed object-oriented system.
3. Exposing tools to LLMs , allowing them to call domain objects.

The third role is novel in the context of LLMs and Embabel.

<!-- image -->

When designing your domain objects, consider which methods should be callable by LLMs and which should not.

Expose methods that LLMs should be able to call using the @Tool annotation:

```
@Tool(description = "Build the project using the given command in the root") ① fun build (command: String ): String { val br = ci. buildAndParse ( BuildOptions (command, true )) return br. relevantOutput () }
```

① The Spring AI @Tool annotation indicates that this method is callable by LLMs.

When an @Action method issues a prompt, tool methods on all domain objects are available to the LLM.

You can also add additional tool methods with the withToolObjects method on PromptRunner .

Domain objects may or may not be persistent. If persistent, they will likely be stored in a familiar JVM technology such
as JPA or JDBC. We advocate the use of Spring Data patterns and repositories, although you are free to use any
persistence technology you like.

## 4.2. Tool Call Choice

When to use MCP or other tools versus method calls in agents

## 4.3. Mixing LLMs

It's good practice to use multiple LLMs in your agentic system. Embabel makes it easy. One key benefit of breaking
functionality into smaller actions is that you can use different LLMs for different actions, depending on their
strengths and weaknesses. You can also the cheapest (greenest) possible LLM for a given task.

## Chapter 5. Contributing

Open source is a wonderful thing. We welcome contributions to the Embabel project.

How to contribute:

- Familiarize yourself with the project by reading the documentation.
- Familiarize yourself with the issue tracker and open pull requests to ensure you're not duplicating something.
- Sign your commits
- Join the Embabel community on Discord at discord.gg/t6bjkyj93q.

Contributions are not limited to code. You can also help by:

- Improving the documentation
- Reporting bugs
- Suggesting new features
- Engaging with the community on Discord
- Creating examples and other materials
- Talking about Embabel at meetups and conferences
- Posting about Embabel on social media

When contributing code, do augment your productivity using coding agents and LLMs, but avoid these pitfalls:

- Excessive LLM comments that add no value . Code should be self-documenting. Comments are for things that are
  non-obvious.
- Bloated PR descriptions and other content.

Nothing personal, but such contributions will automatically be rejected.

<!-- image -->

You must understand anything you contribute.

## Chapter 6. Resources

## 6.1. Rod Johnson's Blog Posts

- Embabel: A new Agent Platform For the JVM - Introduction to the Embabel agent framework, explaining the motivation for
  building an agent platform specifically for the JVM ecosystem. Covers the key differentiators and benefits of the
  approach.
- The Embabel Vision - Rod Johnson's vision for the future of agent frameworks and how Embabel fits into the broader AI
  landscape. Discusses the long-term goals and strategic direction of the project.
- Context Engineering Needs Domain Understanding - Deep dive into the DICE  (DomainIntegrated Context Engineering)
  concept and why domain understanding is fundamental to effective context engineering in AI systems.

## 6.2. Examples and Tutorials

- Creating an AI Agent in Java Using Embabel Agent Framework by Baeldung - A nice introductory example, in Java.
- Building Agents With Embabel: A Hands-On Introduction by Jettro Coenradie - An excellent Java tutorial.

## 6.2.1. Embabel Agent Examples Repository

Comprehensive collection of example agents demonstrating different aspects of the framework:

- Beginner Examples : Simple horoscope agents showing basic concepts
- Intermediate Examples : Multi-LLM research agents with self-improvement
- Advanced Examples : Fact-checking agents with parallel verification and confidence scoring
- Integration Examples : Agents that use web tools, databases, and external APIs

Perfect starting point for learning Embabel development with hands-on examples.

## 6.2.2. Java Agent Template

Template repository for creating new Java-based Embabel agents. Includes:

- Pre-configured project structure
- Example WriteAndReviewAgent demonstrating multi-LLM workflows
- Build scripts and Docker configuration
- Getting started documentation

## 6.2.3. Kotlin Agent Template

Template repository for Kotlin-based agent development with similar features to the Java template

## 6.3. Sophisticated Example: Tripper Travel Planner

## 6.3.1. Tripper - AI-Powered Travel Planning Agent

A production-quality example demonstrating advanced Embabel capabilities:

## Features:

- Generates personalized travel itineraries using multiple AI models
- Integrates web search, mapping, and accommodation search
- Modern web interface built with htmx
- Containerized deployment with Docker
- CI/CD pipeline with GitHub Actions

## Technical Highlights:

- Uses both Claude Sonnet and GPT-4.1-mini models
- Demonstrates domain-driven design principles
- Shows how to build user-facing applications with Embabel
- Practical example of deterministic planning with AI

## Learning Value:

- Real-world application of Embabel concepts
- Integration patterns with external services
- Production deployment considerations
- User interface design for AI applications

## 6.4. Goal-Oriented Action Planning (GOAP)

## 6.4.1. Goal Oriented Action Planning

Introduction to GOAP, the planning algorithm used by Embabel. Explains the core concepts and why GOAP is effective for
AI agent planning.

## 6.4.2. Small Language Model Agents - NVIDIA Research

Research paper discussing the division between "code agency" and "LLM agency" - concepts that inform Embabel's
architecture.

## 6.4.3. OODA Loop - Wikipedia

Background on the Observe-Orient-Decide-Act loop that underlies Embabel's replanning approach.

## 6.5. Domain-Driven Design

## 6.5.1. Domain-Driven Design - Martin Fowler

Foundational concepts of Domain-Driven Design that inform Embabel's approach to domain modeling.

## 6.5.2. Domain-Driven Design: Tackling Complexity in the Heart of Software

Eric Evans' seminal book on DDD principles. Essential reading for understanding how to model complex domains
effectively.

## 6.5.3. DDD and Contextual Validation

Advanced DDD concepts relevant to building sophisticated domain models for AI agents.

## Chapter 7. APPENDIX

## Chapter 8. Planning Module

## 8.1. Abstract

Lower level module for planning and scheduling. Used by Embabel Agent Platform.

## 8.2. A* GOAP Planner Algorithm Overview

The A* GOAP (Goal-Oriented Action Planning) Planner is an implementation of the A* search algorithm specifically
designed for planning sequences of actions to achieve specified goals.

The algorithm efficiently finds the optimal path from an initial world state to a goal state by exploring potential
action sequences and minimizing overall cost.

## 8.2.1. Core Algorithm Components

The A* GOAP Planner consists of several key components:

1. A Search*: Finds optimal action sequences by exploring the state space
2. Forward Planning : Simulates actions from the start state toward goals
3. Backward Planning : Optimizes plans by working backward from goals
4. Plan Simulation : Verifies that plans achieve intended goals
5. Pruning : Removes irrelevant actions to create efficient plans
6. Unknown Condition Handling : Manages incomplete world state information

## 8.2.2. A* Search Algorithm

```
The A* search algorithm operates by maintaining:
```

- Open List : A priority queue of states to explore, ordered by f-score
- Closed Set : States already fully explored
- g-score : Cost accumulated so far to reach a state
- h-score : Heuristic estimate of remaining cost to goal
- f-score : Total estimated cost (g-score + h-score)

## 8.2.3. Process Flow

## 1. Initialization :

- Begin with the start state in the open list
- Set its g-score to 0 and calculate its h-score

## 2. Main Loop :

- While the Open List is not empty:
- Select the state with the lowest f-score from the open list
- If this state satisfies the goal, construct and return the plan
- Otherwise, mark the state as processed (add to closed set)
- For each applicable action, generate the next state and add to open list if it better than existing paths

3. Path Reconstruction : When a goal state is found, reconstruct the path by following predecessors

- Create a plan consisting of the sequence of actions

```
_Reference: link:goap/AStarGoapPlanner.kt[AStarGoapPlanner]:planToGoalFrom:_
```

## 8.2.4. Forward and Backward Planning Optimization

The planner implements a two-pass optimization strategy to eliminate unnecessary actions:

## Backward Planning Optimization

This pass works backward from the goal conditions to identify only actions that contribute to achieving the goal

\_Reference:

link:goap/AStarGoapPlanner.kt[AStarGoapPlanner]:\_backwardPlanningOptimization\_\_\_

## Forward Planning Optimization

This pass simulates the plan from the start state and removes actions that don't make progress toward the goal:

\_Reference:

link:goap/AStarGoapPlanner.kt[AStarGoapPlanner]:\_forwardPlanningOptimization\_\_\_

## Plan Simulation

```
Plan simulation executes actions in sequence to verify the plan's correctness: _Reference: function simulatePlan(startState, actions)_
```

## 8.2.5. Pruning Planning Systems

```
The planner can prune entire planning systems to remove irrelevant actions:
```

```
function prune (planningSystem): // Get all plans to all goals allPlans = plansToGoals (planningSystem) // Keep only actions that appear in at least one plan return planningSystem. copy ( actions = planningSystem.actions. filter { action -> allPlans. any { plan -> plan.actions. contains (action) } }. toSet () )
```

## Heuristic Function

```
The heuristic function estimates the cost to reach the goal from a given state:
```

## 8.2.6. Complete Planning Process

1. Initialize with start state, actions, and goal conditions
2. Run A* search to find an initial action sequence
3. Apply backward planning optimization to eliminate unnecessary actions
4. Apply forward planning optimization to further refine the plan
5. Verify the plan through simulation
6. Return the optimized action sequence or null if no valid plan exists

## 8.3. Agent Pruning Process

```
When pruning an agent for specific goals:
```

1. Identify all known conditions in the planning system
2. Set initial state based on input conditions

3. Find all possible plans to each goal
4. Keep only actions that appear in at least one plan
5. Create a new agent with the pruned action set

This comprehensive approach ensures agents contain only the actions necessary to achieve their designated goals,
improving efficiency and preventing action leakage between different agents.

## 8.3.1. Progress Determination Logic in A* GOAP Planning

The progress determination logic in method *forwardPlanningOptimization* is a critical part of the forward planning
optimization in the A* GOAP algorithm. This logic ensures that only actions that meaningfully progress the state toward
the goal are included in the final plan.

## Progress Determination Expression

```
progressMade = nextState != currentState && action.effects. any { (key, value) -> goal.preconditions. containsKey (key) && currentState[key] != goal.preconditions[key] && (value == goal.preconditions[key] || key not in nextState) }
```

## Detailed Explanation

The expression evaluates to true only when an action makes meaningful progress toward achieving the goal state. Let's
break down each component:

## 1. nextState != currentState

- Verifies that the action actually changes the world state
- Prevents including actions that have no effect

2. action.effects.any { … }

- Examines each effect the action produces
- Returns true if ANY effect satisfies the inner condition

3. goal.preconditions.containsKey(key)

- Ensures we only consider effects that relate to conditions required by the goal
- Ignores effects that modify conditions irrelevant to our goal

4. currentState[key] != goal.preconditions[key]

- Checks that the current condition value differs from what the goal requires
- Only counts progress if we're changing a condition that needs changing

5. (value == goal.preconditions[key] || key not in nextState)

- This checks one of two possible ways an action can make progress:
- value == goal.preconditions[key]
- The action changes the condition to exactly match what the goal requires
- Direct progress toward goal achievement
- key not in nextState
- The action removes the condition from the state entirely
- This is considered progress if the condition was previously in an incorrect state
- Allows for actions that clear obstacles or reset conditions