# NLP Chatbot — Dialogflow Conversational Agent

A full-stack conversational application that combines a Java/Spring backend, a React frontend, Dialogflow NLP, webhooks, and external APIs. The chatbot recognizes user intent and can respond to predefined requests, retrieve jokes, and return city information.

## Quick Links

- **Live Project:** [eaichat.vercel.app](https://eaichat.vercel.app/)
- **API Documentation (Swagger):** [aichat.runmydocker-app.com/swagger-ui.html](https://aichat.runmydocker-app.com/swagger-ui.html)
- **Backend Repository:** [github.com/elad9219/chatbot](https://github.com/elad9219/chatbot)
- **Frontend Repository:** [github.com/elad9219/chatbot-frontend](https://github.com/elad9219/chatbot-frontend)

## Highlights

- **Intent-based conversations:** Uses Dialogflow to recognize user intent and route requests.
- **Webhook integration:** Connects NLP intents to backend logic and external services.
- **External APIs:** Retrieves jokes and city information in real time.
- **Guided user experience:** Provides example prompts to help first-time users understand available interactions.
- **Full-stack implementation:** Java/Spring backend, React frontend, and Docker support.

## Technologies

- **Backend:** Java, Spring Framework, Maven
- **Frontend:** React
- **NLP:** Dialogflow
- **Integration:** Webhooks, REST APIs
- **Containerization:** Docker
- **API Documentation:** Swagger

## Example Interactions

- `Hi` → Returns a greeting.
- `Tell me a joke` → Retrieves a joke from an external jokes API.
- `City of Paris` → Retrieves city information from an external city API.

## Screenshots

### Chat Interface

![Chatbot interface](https://github.com/user-attachments/assets/a32e880b-7683-48cd-9039-9cdcffcb9de1)

### Conversation Example

![Chatbot conversation](https://github.com/user-attachments/assets/70f2bfb4-1a51-4027-bcd2-3552bd9f3264)

## Local Setup

### Backend

```bash
git clone https://github.com/elad9219/chatbot.git
cd chatbot
mvn clean install
mvn spring-boot:run
```

Configure your own Dialogflow credentials and any required external API configuration before starting the backend. Do not commit real credentials or service-account keys.

### Frontend

```bash
git clone https://github.com/elad9219/chatbot-frontend.git
cd chatbot-frontend
npm install
npm start
```

## Dialogflow Setup

1. Create or select a Dialogflow agent.
2. Configure intents and entities for the supported conversation flows.
3. Configure the backend webhook.
4. Provide the required Google Cloud credentials securely through your local environment/configuration.

## Contact

- **Elad Tennenboim**
- **GitHub:** [elad9219](https://github.com/elad9219)
- **LinkedIn:** [linkedin.com/in/elad-tennenboim](https://www.linkedin.com/in/elad-tennenboim/)
- **Email:** elad9219@gmail.com
