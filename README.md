# Chatbot Project

## Description

This project is a chatbot application that allows users to interact with a conversational agent. The chatbot can respond to predefined queries, fetch jokes from an external API, and provide information about cities using another external API. It leverages natural language processing through Dialogflow to understand and process user inputs, offering a seamless and engaging user experience.

## Features

- Predefined responses for common queries like "hi" and "how are you."
- Integration with an external Jokes API to fetch and display random jokes.
- Integration with an external City Information API to retrieve and present details about cities.
- Natural language processing capabilities powered by Dialogflow.
- User-friendly web interface built with React.ts.
- Helpful prompts for first-time users to guide them on how to interact with the chatbot.

## Technologies Used

- **Backend**: Java, Spring Framework
- **Frontend**: React.ts
- **Natural Language Processing**: Dialogflow
- **External APIs**: Jokes API, City Information API
- **Containerization**: Docker

## Setup Instructions

1. **Clone the Repositories**:
   - Backend: `git clone https://github.com/elad9219/chatbot.git`
   - Frontend: `git clone https://github.com/elad9219/chatbot-frontend.git`

2. **Backend Setup**:
   - Navigate to the backend directory: `cd chatbot`
   - Ensure you have Java and Maven installed.
   - Configure environment variables (e.g., API keys for Dialogflow, Jokes API, and City Information API).
   - Run `mvn spring-boot:run` to start the backend server.

3. **Frontend Setup**:
   - Navigate to the frontend directory: `cd chatbot-frontend`
   - Ensure you have Node.js and npm installed.
   - Run `npm install` to install dependencies.
   - Run `npm start` to start the frontend development server.

4. **Dialogflow Configuration**:
   - Set up a Dialogflow agent and configure intents and entities for basic queries, joke requests, and city information requests.
   - Integrate the Dialogflow API with the backend by providing the necessary credentials (e.g., Google Cloud service account key).

## Usage

- Access the chatbot via the web interface at `http://localhost:3000` (or the deployed URL: [https://bot.runmydocker-app.com/](https://bot.runmydocker-app.com/)).
- Type messages like:
  - "Hi" → Get a friendly greeting.
  - "Tell me a joke" → Receive a random joke from chucknorris.io API.
  - "Tell me about Tel Aviv" → Get information about the specified city from the City API.
- First-time users will see helpful prompts to guide them on what to ask.

## Screenshots



## Links

- **Live Project**: [https://bot.runmydocker-app.com/](https://bot.runmydocker-app.com/)
- **Swagger UI**: [https://bot.runmydocker-app.com/swagger-ui.html](https://bot.runmydocker-app.com/swagger-ui.html)
- **Backend Repository**: [https://github.com/elad9219/chatbot](https://github.com/elad9219/chatbot)
- **Frontend Repository**: [https://github.com/elad9219/chatbot-frontend](https://github.com/elad9219/chatbot-frontend)
